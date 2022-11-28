package name.space;

public class CallerClass{
    public static void callMethod(){
        Parent firstChild = new FirstChild();
        firstChild.method();

        Parent seconChild = new SecondChild();
        seconChild.method();
    }

    public static void main(String[] args) {

    }
}

interface Parent{
    public void method();
}

class FirstChild implements Parent{
    @Override
    public void method() {
    }
}

class SecondChild implements Parent{
    @Override
    public void method() {
    }
}
