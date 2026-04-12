package ch.adibilis.jtg.model.types;

import java.util.ArrayList;
import java.util.List;

public final class ObjectType implements Type {

    private final String name;
    private final String packageSegment;
    private final List<String> genericParams;
    private List<Field> fields = new ArrayList<>();
    private List<Type> genericArgInstantiations = new ArrayList<>();

    public ObjectType(String name, String packageSegment, List<String> genericParams) {
        this.name = name;
        this.packageSegment = packageSegment;
        this.genericParams = List.copyOf(genericParams);
    }

    public ObjectType copy() {
        ObjectType copy = new ObjectType(name, packageSegment, genericParams);
        copy.fields = new ArrayList<>(fields);
        copy.genericArgInstantiations = new ArrayList<>(genericArgInstantiations);
        return copy;
    }

    public String getName() { return name; }
    public String getPackageSegment() { return packageSegment; }
    public List<String> getGenericParams() { return genericParams; }
    public List<Field> getFields() { return fields; }
    public void setFields(List<Field> fields) { this.fields = fields; }
    public List<Type> getGenericArgInstantiations() { return genericArgInstantiations; }
    public void setGenericArgInstantiations(List<Type> genericArgInstantiations) {
        this.genericArgInstantiations = genericArgInstantiations;
    }
}
