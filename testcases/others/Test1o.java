class A {
    int n;
    int getN() {
        return n;
    }
    void setN(int n) {
        this.n = n;
    }
}

class Test {
    A func(A x, A y) {
        A z = new A();
        for (; y.getN() > 0; y.setN(y.getN()-1)) {
            z.setN(z.getN() + x.getN());
            x.setN(x.getN()-1);
        }
        return z;
    }
}
