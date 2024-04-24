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
}
