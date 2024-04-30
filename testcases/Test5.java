class A {
    int n;
}

class B {
    long m;
}

class Test {
    void func1(A a){
        B b = new B();
        b.m = a.n;
        while (b.m > 0) {
            b.m--;
            if (b.m == 5) {
                b = new B();
                b.m = 4;
            }
            a.n++;
        }
    }
    void func2(A a){
        B b = new B();
        b.m = a.n;
        while (b.m > 0) {
            b.m--;
            a.n++;
        }
    }
    public static void main(String[] args) {
        A a = new A();
        a.n = 30;
        Test t = new Test();
        t.func1(a);
        a.n = 20;
        t.func2(a);
    }
}
