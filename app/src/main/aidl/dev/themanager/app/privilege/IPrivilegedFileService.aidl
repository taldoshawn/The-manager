package dev.themanager.app.privilege;

import android.os.ParcelFileDescriptor;
import dev.themanager.app.privilege.RemoteFile;

interface IPrivilegedFileService {
    void destroy() = 16777114;
    int uid() = 1;
    List<RemoteFile> list(String path, int offset, int limit) = 2;
    RemoteFile stat(String path) = 3;
    boolean createDirectory(String path) = 4;
    boolean createFile(String path) = 5;
    boolean copy(String source, String target) = 6;
    boolean move(String source, String target) = 7;
    boolean delete(String path) = 8;
    ParcelFileDescriptor openRead(String path) = 9;
    String beginAtomicWrite(String target) = 10;
    ParcelFileDescriptor openPendingWrite(String token) = 11;
    boolean commitAtomicWrite(String token) = 12;
    void abortAtomicWrite(String token) = 13;
    String sha256(String path) = 14;
}
