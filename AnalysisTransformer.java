import java.util.*;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.toolkits.graph.*;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class AnalysisTransformer extends BodyTransformer {
    private Lock lock = new ReentrantLock();
    boolean FieldPrivatizationStart = false;
    Set<SootClass> FieldPrivatizedClasses = new HashSet<SootClass>();
    Set<SootClass> RemainingClasses = new HashSet<SootClass>();
    private final Semaphore barrier = new Semaphore(0);
    private int count = 0;
    @Override
    protected void internalTransform(Body body, String phaseName, Map<String, String> options) {
        // PHASE 1 - Field Privatization
        lock.lock();
        if (!FieldPrivatizationStart) {
            for (SootClass c : Scene.v().getApplicationClasses()) {
                if (c.isPhantom() || c.isInterface() || c.isAbstract() || c.isStatic()) continue;
                FieldPrivatizedClasses.add(c);
            }
            RemainingClasses.addAll(FieldPrivatizedClasses);
            FieldPrivatizationStart = true;
        }
        boolean done = true;
        SootClass c = body.getMethod().getDeclaringClass();
        if (RemainingClasses.contains(c)) {
            done = false;
            RemainingClasses.remove(c);
        }
        lock.unlock();

        if (!done) PrivatizeFields(c);

        // BARRIER
        lock.lock();
        if (!done) count++;
        lock.unlock();
        if (count == FieldPrivatizedClasses.size())
            barrier.release();
        try {
            barrier.acquire();
        } catch (InterruptedException e) {}
        barrier.release();

        // PHASE 2 - Replace all reads of fields with getter methods and all writes with setter methods

        // PHASE 3 - Optimization - decreasing number of loads and stores by reducing unnecessary getter and setter calls
        
    }

    private void PrivatizeFields(SootClass c) {
        SootClass declaringClass = c;
        for (SootField field : declaringClass.getFields()) {
            if (!field.isStatic()) {
                field.setModifiers(Modifier.PRIVATE);
                // Add getter method
                SootMethod getter = new SootMethod("get" + capitalizeFirstLetter(field.getName()),
                Collections.emptyList(),
                field.getType(),
                Modifier.PUBLIC);
                declaringClass.addMethod(getter);
                JimpleBody mbody = Jimple.v().newBody(getter);
                getter.setActiveBody(mbody);
                Local thisLocal = Jimple.v().newLocal("thisLocal", RefType.v(declaringClass.getName()));
                Local local = Jimple.v().newLocal("local", field.getType());
                mbody.getLocals().add(thisLocal);
                mbody.getLocals().add(local);
                PatchingChain<Unit> units = mbody.getUnits();
                units.add(Jimple.v().newIdentityStmt(thisLocal, Jimple.v().newThisRef(RefType.v(declaringClass.getName()))));
                units.add(Jimple.v().newAssignStmt(local, Jimple.v().newInstanceFieldRef(thisLocal, field.makeRef())));
                units.add(Jimple.v().newReturnStmt(local));
                // Add setter method
                SootMethod setter = new SootMethod("set" + capitalizeFirstLetter(field.getName()),
                Arrays.asList(field.getType()),
                VoidType.v(),
                Modifier.PUBLIC);
                declaringClass.addMethod(setter);
                mbody = Jimple.v().newBody(setter);
                mbody.setMethod(setter);
                setter.setActiveBody(mbody);
                thisLocal = Jimple.v().newLocal("thisLocal", RefType.v(declaringClass.getName()));
                Local paramLocal = Jimple.v().newLocal("paramLocal", field.getType());
                mbody.getLocals().add(thisLocal);
                mbody.getLocals().add(paramLocal);
                units = mbody.getUnits();
                units.add(Jimple.v().newIdentityStmt(thisLocal, Jimple.v().newThisRef(RefType.v(declaringClass))));
                units.add(Jimple.v().newIdentityStmt(paramLocal, new ParameterRef(field.getType(), 0)));
                units.add(Jimple.v().newAssignStmt(Jimple.v().newInstanceFieldRef(thisLocal, field.makeRef()), paramLocal));
                units.add(Jimple.v().newReturnVoidStmt());
            }
        }
    }

    private String capitalizeFirstLetter(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
