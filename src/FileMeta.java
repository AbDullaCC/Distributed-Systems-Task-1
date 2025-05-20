import java.util.List; // For FileMeta's nodes list

// Placeholder for FileMeta class - should be Serializable
// This class would typically hold metadata about a file.
class FileMeta implements java.io.Serializable {
    private static final long serialVersionUID = 1L; // Recommended for Serializable classes
    String name;
    String dep; // Department
    List<String> nodes; // List of node IDs where this file (or its replicas) are stored

    public FileMeta(String name, String dep, List<String> nodes) {
        this.name = name;
        this.dep = dep;
        this.nodes = nodes;
    }

    public String getName() {
        return name;
    }

    public String getDep() {
        return dep;
    }

    public List<String> getNodes() {
        return nodes;
    }

    @Override
    public String toString() {
        return "FileMeta{" +
                "name='" + name + '\'' +
                ", dep='" + dep + '\'' +
                ", nodes=" + nodes +
                '}';
    }
}