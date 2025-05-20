public class ActualFile {
    FileMeta meta;
    byte[] content;

    public ActualFile(FileMeta meta, byte[] content) {
        this.meta = meta;
        this.content = content;
    }

    public ActualFile(String name, String dep, byte[] content) {
        this.meta = new FileMeta(name, dep);
        this.content = content;
    }
}
