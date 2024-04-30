class A {
    int n;
}

class Test {
    void func1(){
        A a = new A();
        return;
    }
    A func2(A x, A y) {
        A z = new A();
        for (; y.n > 0; y.n--) {
            z.n = z.n + x.n;
            x.n--;
        }
        return z;
    }
    public static void main(String[] args) {
        A a = new A();
        a.n = 0;
        Test t = new Test();
        t.func1();
        A b = new A();
        b.n = 17;
        A c = new A();
        c.n = 10;
        A ans = t.func2(a, a);
    }
}
