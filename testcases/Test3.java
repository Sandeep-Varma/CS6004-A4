class A {
    int n;
}

class Test {
    void func(A a){
        if (a.n == 0) return;
        a.n--;
        func(a);
    }
}
