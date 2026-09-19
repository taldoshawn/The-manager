package dev.themanager.app.privilege;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.StructStat;

import androidx.annotation.Keep;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Keep
public final class PrivilegedFileService extends IPrivilegedFileService.Stub {
    private static final int MAX_PAGE_SIZE = 1000;
    private static final java.util.Set<String> PROTECTED_ROOTS = new java.util.HashSet<>(java.util.Arrays.asList(
            "/", "/system", "/system_ext", "/vendor", "/product", "/data", "/storage",
            "/sdcard", "/storage/emulated", "/storage/emulated/0"
    ));
    private final Map<String, PendingWrite> pendingWrites = new ConcurrentHashMap<>();

    public PrivilegedFileService() {
    }

    @Keep
    public PrivilegedFileService(Context ignored) {
    }

    @Override
    public void destroy() {
        for (String token : new ArrayList<>(pendingWrites.keySet())) {
            abortAtomicWrite(token);
        }
        System.exit(0);
    }

    @Override
    public int uid() {
        return Os.getuid();
    }

    @Override
    public List<RemoteFile> list(String rawPath, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Invalid page.");
        }
        Path directory = safePath(rawPath);
        if (!Files.isDirectory(directory)) throw new IllegalArgumentException("Not a directory.");
        try (java.util.stream.Stream<Path> stream = Files.list(directory)) {
            return stream
                    .sorted(Comparator.comparing((Path p) -> !Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS))
                            .thenComparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .skip(offset)
                    .limit(limit)
                    .map(this::toRemoteFile)
                    .collect(Collectors.toList());
        } catch (IOException error) {
            throw new IllegalStateException("Cannot list directory.", error);
        }
    }

    @Override
    public RemoteFile stat(String rawPath) {
        Path path = safePath(rawPath);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("Path not found.");
        return toRemoteFile(path);
    }

    @Override
    public boolean createDirectory(String rawPath) {
        try {
            Files.createDirectory(safePath(rawPath));
            return true;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot create directory.", error);
        }
    }

    @Override
    public boolean createFile(String rawPath) {
        try {
            Files.createFile(safePath(rawPath));
            return true;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot create file.", error);
        }
    }

    @Override
    public boolean copy(String rawSource, String rawTarget) {
        Path source = safePath(rawSource);
        Path target = safePath(rawTarget);
        rejectNestedTarget(source, target);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("Target exists.");
        try {
            copyTree(source, target);
            return true;
        } catch (IOException error) {
            tryDeleteTree(target);
            throw new IllegalStateException("Copy failed.", error);
        }
    }

    @Override
    public boolean move(String rawSource, String rawTarget) {
        Path source = safePath(rawSource);
        requireDestructivePath(source);
        Path target = safePath(rawTarget);
        rejectNestedTarget(source, target);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("Target exists.");
        try {
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException crossDevice) {
                copyTree(source, target);
                deleteTree(source);
            }
            return true;
        } catch (IOException error) {
            throw new IllegalStateException("Move failed.", error);
        }
    }

    @Override
    public boolean delete(String rawPath) {
        try {
            Path target = safePath(rawPath);
            requireDestructivePath(target);
            deleteTree(target);
            return true;
        } catch (IOException error) {
            throw new IllegalStateException("Delete failed.", error);
        }
    }

    @Override
    public ParcelFileDescriptor openRead(String rawPath) {
        try {
            return ParcelFileDescriptor.open(safePath(rawPath).toFile(), ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot open file.", error);
        }
    }

    @Override
    public String beginAtomicWrite(String rawTarget) {
        Path target = safePath(rawTarget);
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) throw new IllegalArgumentException("Invalid parent.");
        try {
            Path temporary = Files.createTempFile(parent, ".themanager-", ".tmp");
            int mode = -1;
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                mode = Os.stat(target.toString()).st_mode & 0777;
            }
            String token = UUID.randomUUID().toString();
            pendingWrites.put(token, new PendingWrite(temporary, target, mode));
            return token;
        } catch (Exception error) {
            throw new IllegalStateException("Cannot prepare write.", error);
        }
    }

    @Override
    public ParcelFileDescriptor openPendingWrite(String token) {
        PendingWrite pending = requirePending(token);
        try {
            return ParcelFileDescriptor.open(
                    pending.temporary.toFile(),
                    ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_TRUNCATE
            );
        } catch (IOException error) {
            throw new IllegalStateException("Cannot open pending write.", error);
        }
    }

    @Override
    public boolean commitAtomicWrite(String token) {
        PendingWrite pending = requirePending(token);
        try {
            try (FileOutputStream sync = new FileOutputStream(pending.temporary.toFile(), true)) {
                sync.getFD().sync();
            }
            try {
                Files.move(
                        pending.temporary,
                        pending.target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (IOException noAtomicMove) {
                Files.move(pending.temporary, pending.target, StandardCopyOption.REPLACE_EXISTING);
            }
            if (pending.mode >= 0) Os.chmod(pending.target.toString(), pending.mode);
            pendingWrites.remove(token);
            return true;
        } catch (Exception error) {
            throw new IllegalStateException("Cannot commit write.", error);
        }
    }

    @Override
    public void abortAtomicWrite(String token) {
        PendingWrite pending = pendingWrites.remove(token);
        if (pending != null) {
            try {
                Files.deleteIfExists(pending.temporary);
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public String sha256(String rawPath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream input = Files.newInputStream(safePath(rawPath))) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) result.append(String.format("%02x", value & 0xff));
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException("Hash failed.", error);
        }
    }

    private PendingWrite requirePending(String token) {
        PendingWrite pending = pendingWrites.get(token);
        if (pending == null) throw new IllegalArgumentException("Unknown write token.");
        return pending;
    }

    private Path safePath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty() || rawPath.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid path.");
        }
        return Paths.get(rawPath).toAbsolutePath().normalize();
    }

    private void requireDestructivePath(Path path) {
        if (PROTECTED_ROOTS.contains(path.toString())) {
            throw new SecurityException("Protected root.");
        }
    }

    private RemoteFile toRemoteFile(Path path) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    path,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS
            );
            String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
            String permissions = "";
            try {
                StructStat stat = Os.lstat(path.toString());
                permissions = Integer.toOctalString(stat.st_mode & 0777);
            } catch (Exception ignored) {
            }
            return new RemoteFile(
                    path.toString(),
                    name,
                    attributes.isDirectory(),
                    attributes.isSymbolicLink(),
                    attributes.isDirectory() ? 0 : attributes.size(),
                    attributes.lastModifiedTime().toMillis(),
                    permissions,
                    Files.isReadable(path),
                    Files.isWritable(path),
                    name.startsWith(".")
            );
        } catch (IOException error) {
            throw new IllegalStateException("Cannot stat path.", error);
        }
    }

    private void rejectNestedTarget(Path source, Path target) {
        if (source.equals(target)) throw new IllegalArgumentException("Source equals target.");
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) && target.startsWith(source)) {
            throw new IllegalArgumentException("Target is inside source.");
        }
    }

    private void copyTree(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            Files.copy(source, target, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES);
            return;
        }
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                Files.createDirectory(target.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.copy(
                        file,
                        target.resolve(source.relativize(file)),
                        LinkOption.NOFOLLOW_LINKS,
                        StandardCopyOption.COPY_ATTRIBUTES
                );
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteTree(Path target) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(target, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void tryDeleteTree(Path target) {
        try {
            deleteTree(target);
        } catch (IOException ignored) {
        }
    }

    private static final class PendingWrite {
        final Path temporary;
        final Path target;
        final int mode;

        PendingWrite(Path temporary, Path target, int mode) {
            this.temporary = temporary;
            this.target = target;
            this.mode = mode;
        }
    }
}
