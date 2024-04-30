class A {
    int n;
}

class Test {
    A func(A x) {
        for (int i=0; i < 100; i++) {
            x.n++;
        }
        return x;
    }
    public static void main(String[] args) {
        A a = new A();
        a.n = 0;
        Test t = new Test();
        t.func(a);
    }
}
