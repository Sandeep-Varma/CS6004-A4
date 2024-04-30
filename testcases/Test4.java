class X {
    int z;
}

class A {
    int n;
    X x;
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
        a.n = 10;
        Test t = new Test();
        t.func1();
        A b = t.func2(a, a);
    }
}
