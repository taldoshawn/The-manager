package dev.themanager.app.privilege;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public final class RemoteFile implements Parcelable {
    public final String path;
    public final String name;
    public final boolean directory;
    public final boolean symlink;
    public final long size;
    public final long modifiedAt;
    public final String permissions;
    public final boolean readable;
    public final boolean writable;
    public final boolean hidden;

    public RemoteFile(
            String path,
            String name,
            boolean directory,
            boolean symlink,
            long size,
            long modifiedAt,
            String permissions,
            boolean readable,
            boolean writable,
            boolean hidden
    ) {
        this.path = path;
        this.name = name;
        this.directory = directory;
        this.symlink = symlink;
        this.size = size;
        this.modifiedAt = modifiedAt;
        this.permissions = permissions;
        this.readable = readable;
        this.writable = writable;
        this.hidden = hidden;
    }

    private RemoteFile(Parcel source) {
        path = source.readString();
        name = source.readString();
        directory = source.readByte() != 0;
        symlink = source.readByte() != 0;
        size = source.readLong();
        modifiedAt = source.readLong();
        permissions = source.readString();
        readable = source.readByte() != 0;
        writable = source.readByte() != 0;
        hidden = source.readByte() != 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel destination, int flags) {
        destination.writeString(path);
        destination.writeString(name);
        destination.writeByte((byte) (directory ? 1 : 0));
        destination.writeByte((byte) (symlink ? 1 : 0));
        destination.writeLong(size);
        destination.writeLong(modifiedAt);
        destination.writeString(permissions);
        destination.writeByte((byte) (readable ? 1 : 0));
        destination.writeByte((byte) (writable ? 1 : 0));
        destination.writeByte((byte) (hidden ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<RemoteFile> CREATOR = new Creator<RemoteFile>() {
        @Override
        public RemoteFile createFromParcel(Parcel source) {
            return new RemoteFile(source);
        }

        @Override
        public RemoteFile[] newArray(int size) {
            return new RemoteFile[size];
        }
    };
}
