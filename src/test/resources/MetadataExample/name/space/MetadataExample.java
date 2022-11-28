package name.space;

class MetadataExample {
    public MetadataExample() {
    }

    public static void publicMethod() {
    }

    private static void privateMethod() {
    }

    void packagePrivateMethod(){
    }

    protected void protectedMethod(){
    }

    private class InnerPrivateClass{
        public void innerClassPublicMethod(){}
    }

    protected class InnerProtectedClass{

    }
    public static void main(String[] args) {
    }
}

abstract class AbstractClass{
    public abstract void abstractMethod();
}

interface IMetadata{
    public void iMethod();
    default void defaultMethod(){
    }
}

final class FinalCLass{
}

class ExtendingClass extends MetadataExample{

}

class ImplementingClass extends ExtendingClass implements IMetadata{
    public void iMethod(){}
}