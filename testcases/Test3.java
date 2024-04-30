class A {
    int n;
}

class Test {
    void func(A a){
        if (a.n == 0) return;
        a.n--;
        func(a);
    }
    public static void main(String[] args) {
        A a = new A();
        a.n = 20;
        Test t = new Test();
        t.func(a);
    }
}
