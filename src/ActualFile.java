
// Placeholder for ActualFile class - should be Serializable
// This class would hold the file's metadata and its actual content.
class ActualFile implements java.io.Serializable {
    private static final long serialVersionUID = 1L; // Recommended for Serializable classes
    FileMeta meta;
    byte[] fileContent; // Representing file content as a byte array

    public ActualFile(FileMeta meta, byte[] fileContent) {
        this.meta = meta;
        this.fileContent = fileContent;
    }

    public FileMeta getMeta() {
        return meta;
    }

    public byte[] getFileContent() {
        return fileContent;
    }

    public void setFileContent(byte[] fileContent) {
        this.fileContent = fileContent;
    }


    @Override
    public String toString() {
        return "ActualFile{" +
                "meta=" + meta +
                ", fileContent_length=" + (fileContent != null ? fileContent.length : 0) +
                '}';
    }
}