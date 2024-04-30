class A {
    int n;
}

class Test {
    A func(A x) {
        for (int i=0; i < 10; i++) {
            x.n++;
        }
        return x;
    }
}
