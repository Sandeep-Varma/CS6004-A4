class A {
    int n;
}

class Test {
    void func(A a){
        A b = new A();
        b.n = a.n;
        while (b.n > 0) {
            b.n--;
            // if (b.n == 5) {
            //     b = new B();
            //     b.n = 4;
            // }
            a.n++;
        }
    }
}
