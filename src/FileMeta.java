import java.util.HashSet;
import java.util.List;

public class FileMeta {
    private final HashSet<String> nodes;
    String name;
    String dep;

    public FileMeta(String name, String dep, List<String> nodes) {
        this.name = name;
        this.dep = dep;
        this.nodes = new HashSet<>(nodes);
    }

    public FileMeta(String name, String dep) {
        this.name = name;
        this.dep = dep;
        this.nodes = new HashSet<>();
    }

    public void addNode(String node) {
        this.nodes.add(node);
    }

    public void addNodes(List<String> nodes) {
        this.nodes.addAll(nodes);
    }

    public void removeNode(String node) {
        this.nodes.remove(node);
    }

    public void removeNodes(List<String> nodes) {
        nodes.forEach(this.nodes::remove);
    }

    public List<String> getNodes() {
        return this.nodes.stream().toList();
    }
}
