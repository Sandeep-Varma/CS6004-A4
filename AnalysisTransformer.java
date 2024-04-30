import java.util.*;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.jimple.toolkits.annotation.logic.Loop;
import soot.toolkits.graph.*;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class AnalysisTransformer extends BodyTransformer {
    public boolean optimize;
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
        ReplaceFieldAccesses(body);

        // PHASE 3 - Optimization - decreasing number of loads and stores by reducing unnecessary getter and setter calls
        if (this.optimize) {
            for (Loop loop : new LoopNestTree(body)) {
                MinimizeGetterSetterCalls(body, loop);
            }
        }
    }

    private void MinimizeGetterSetterCalls(Body body, Loop loop) {
        // objects which are used in the loop and are of classes that have been privatized
        Set<Local> selectedObjects = toBeLocalized(loop);
        // for each object in selectedObjects, create local variables for all its fields before the loop
        // and add assignments to the local variables using getter methods before the loop
        // and add assignments to the fields using setter methods after the loop
        Map<String, Local> localMap = new HashMap<String, Local>();
        for (Local l : selectedObjects) {
            if (l.getType() instanceof soot.RefType) {
                SootClass c = ((RefType) l.getType()).getSootClass();
                for (SootField field : c.getFields()) {
                    if (!field.isStatic()) {
                        Local local = Jimple.v().newLocal(l.getName() + "_" + field.getName(), field.getType());
                        body.getLocals().add(local);
                        localMap.put(field.getName(), local);
                        SootMethod getter = c.getMethodByName("get" + capitalizeFirstLetter(field.getName()));
                        SootMethod setter = c.getMethodByName("set" + capitalizeFirstLetter(field.getName()));
                        if (getter != null) {
                            Unit newUnit = Jimple.v().newAssignStmt(local, Jimple.v().newVirtualInvokeExpr(l, getter.makeRef()));
                            body.getUnits().insertAfter(newUnit, body.getUnits().getPredOf(loop.getLoopStatements().get(0)));
                        }
                        if (setter != null) {
                            Unit newUnit = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(l, setter.makeRef(), local));
                            body.getUnits().insertBefore(newUnit, body.getUnits().getSuccOf(loop.getLoopStatements().get(loop.getLoopStatements().size() - 1)));
                        }
                    }
                }
            }
        }
        // Then, replace all getter and setter calls of the object's fields in the loop with the local variable
        for (Unit u : loop.getLoopStatements()) {
            // replacing setter method
            if (u instanceof JInvokeStmt) {
                JInvokeStmt stmt = (JInvokeStmt) u;
                if (stmt.getInvokeExpr() instanceof JVirtualInvokeExpr) {
                    JVirtualInvokeExpr expr = (JVirtualInvokeExpr) stmt.getInvokeExpr();
                    if (expr.getMethod().getName().startsWith("set")) {
                        Local referredObj = (Local) expr.getBase();
                        Local local = localMap.get(expr.getMethod().getName().substring(3).toLowerCase());
                        Unit newUnit = Jimple.v().newAssignStmt(local, expr.getArg(0));
                        body.getUnits().insertBefore(newUnit, u);
                        body.getUnits().remove(u);
                    }
                }
            }
            // replacing getter method
            if (u instanceof JAssignStmt) {
                JAssignStmt stmt = (JAssignStmt) u;
                Value rightOp = stmt.getRightOp();
                if (rightOp instanceof JVirtualInvokeExpr) {
                    JVirtualInvokeExpr expr = (JVirtualInvokeExpr) rightOp;
                    if (expr.getMethod().getName().startsWith("get")) {
                        Local referredObj = (Local) expr.getBase();
                        Local local = localMap.get(expr.getMethod().getName().substring(3).toLowerCase());
                        Unit newUnit = Jimple.v().newAssignStmt(stmt.getLeftOp(), local);
                        body.getUnits().insertBefore(newUnit, u);
                        body.getUnits().remove(u);
                    }
                }
            }
        }

        body.validate();
    }
    
    private Set<Local> toBeLocalized(Loop loop) {
        Set<Local> selectedObjects = new HashSet<Local>();
        // all objects used in the loop
        Set<Local> allObjects = new HashSet<Local>();
        for (Unit u : loop.getLoopStatements()) {
            if (u instanceof JAssignStmt) {
                JAssignStmt stmt = (JAssignStmt) u;
                Value leftOp = stmt.getLeftOp();
                Value rightOp = stmt.getRightOp();
                if (leftOp instanceof JInstanceFieldRef) {
                    JInstanceFieldRef fieldRef = (JInstanceFieldRef) leftOp;
                    Local referredObj = (Local) fieldRef.getBase();
                    allObjects.add(referredObj);
                    if (FieldPrivatizedClasses.contains(fieldRef.getField().getDeclaringClass())) {
                        selectedObjects.add(referredObj);
                    }
                }
                if (rightOp instanceof JInstanceFieldRef) {
                    JInstanceFieldRef fieldRef = (JInstanceFieldRef) rightOp;
                    Local referredObj = (Local) fieldRef.getBase();
                    allObjects.add(referredObj);
                    if (FieldPrivatizedClasses.contains(fieldRef.getField().getDeclaringClass())) {
                        selectedObjects.add(referredObj);
                    }
                }
            }
            else if (u instanceof JInvokeStmt) {
                JInvokeStmt stmt = (JInvokeStmt) u;
                if (stmt.getInvokeExpr() instanceof JVirtualInvokeExpr) {
                    JVirtualInvokeExpr expr = (JVirtualInvokeExpr) stmt.getInvokeExpr();
                    if (expr.getBase() instanceof Local) {
                        Local referredObj = (Local) expr.getBase();
                        allObjects.add(referredObj);
                        if (FieldPrivatizedClasses.contains(expr.getMethod().getDeclaringClass())) {
                            selectedObjects.add(referredObj);
                        }
                    }
                }
            }
            else if (u instanceof JReturnStmt) {
                JReturnStmt stmt = (JReturnStmt) u;
                if (stmt.getOp() instanceof JInstanceFieldRef) {
                    JInstanceFieldRef fieldRef = (JInstanceFieldRef) stmt.getOp();
                    Local referredObj = (Local) fieldRef.getBase();
                    allObjects.add(referredObj);
                    if (FieldPrivatizedClasses.contains(fieldRef.getField().getDeclaringClass())) {
                        selectedObjects.add(referredObj);
                    }
                }
            }
            else if (u instanceof JIfStmt) {
                JIfStmt stmt = (JIfStmt) u;
                if (stmt.getCondition() instanceof JInstanceFieldRef) {
                    JInstanceFieldRef fieldRef = (JInstanceFieldRef) stmt.getCondition();
                    Local referredObj = (Local) fieldRef.getBase();
                    allObjects.add(referredObj);
                    if (FieldPrivatizedClasses.contains(fieldRef.getField().getDeclaringClass())) {
                        selectedObjects.add(referredObj);
                    }
                }
            }
            else if (u instanceof JGotoStmt) {
                JGotoStmt stmt = (JGotoStmt) u;
                if (stmt.getTarget() instanceof JInstanceFieldRef) {
                    JInstanceFieldRef fieldRef = (JInstanceFieldRef) stmt.getTarget();
                    Local referredObj = (Local) fieldRef.getBase();
                    allObjects.add(referredObj);
                    if (FieldPrivatizedClasses.contains(fieldRef.getField().getDeclaringClass())) {
                        selectedObjects.add(referredObj);
                    }
                }
            }
        }
        // Remove objects which are allocated in the loop from selectedObjects
        for (Unit u : loop.getLoopStatements()) {
            if (u instanceof JAssignStmt) {
                JAssignStmt stmt = (JAssignStmt) u;
                Value rightOp = stmt.getRightOp();
                if (rightOp instanceof JNewExpr) {
                    Local newLocal = (Local) stmt.getLeftOp();
                    selectedObjects.remove(newLocal);
                }
            }
        }
        // for any method calls in the loop, get the list of objects which are reachable from any of the method calls
        // for this, use soot's points to analysis
        Set<Local> reachableObjects = new HashSet<Local>();
        PointsToAnalysis pta = Scene.v().getPointsToAnalysis();
        for (Unit u : loop.getLoopStatements()) {
            if (u instanceof JInvokeStmt) {
                JInvokeStmt stmt = (JInvokeStmt) u;
                if (stmt.getInvokeExpr() instanceof JVirtualInvokeExpr) {
                    JVirtualInvokeExpr expr = (JVirtualInvokeExpr) stmt.getInvokeExpr();
                    // if the method is getter or setter, then ignore
                    if (expr.getMethod().getName().startsWith("get") || expr.getMethod().getName().startsWith("set")) continue;
                    // object on which the method is called
                    if (expr.getBase() instanceof Local) {
                        Local referredObj = (Local) expr.getBase();
                        for (Local l : allObjects) {
                            if (pta.reachingObjects(l).hasNonEmptyIntersection(pta.reachingObjects(referredObj))) {
                                reachableObjects.add(l);
                            }
                        }
                    }
                    // arguments of the method call
                    for (Value arg : expr.getArgs()) {
                        if (arg instanceof Local) {
                            Local referredObj = (Local) arg;
                            for (Local l : allObjects) {
                                if (pta.reachingObjects(l).hasNonEmptyIntersection(pta.reachingObjects(referredObj))) {
                                    reachableObjects.add(l);
                                }
                            }
                        }
                    }
                }
            }
        }
        // remove reachable objects from selectedObjects
        selectedObjects.removeAll(reachableObjects);
        return selectedObjects;
    }
    
    private void ReplaceFieldAccesses(Body body) {
        PatchingChain<Unit> units = body.getUnits();
        Iterator<Unit> it = units.snapshotIterator();
        while(it.hasNext()) {
            Unit u = it.next();
            if (u instanceof JAssignStmt) {
                JAssignStmt stmt = (JAssignStmt) u;
                Value leftOp = stmt.getLeftOp();
                Value rightOp = stmt.getRightOp();
                if (rightOp instanceof JInstanceFieldRef) {
                    if (!FieldPrivatizedClasses.contains(((JInstanceFieldRef) rightOp).getField().getDeclaringClass())) continue;
                    JInstanceFieldRef fieldRef = (JInstanceFieldRef) rightOp;
                    Local referredObj = (Local) fieldRef.getBase();
                    SootField field = fieldRef.getField();
                    SootMethod getter = field.getDeclaringClass().getMethodByName("get" + capitalizeFirstLetter(field.getName()));
                    if (getter != null) {
                        Unit newUnit = Jimple.v().newAssignStmt(leftOp, Jimple.v().newVirtualInvokeExpr(referredObj, getter.makeRef()));
                        units.insertBefore(newUnit, u);
                        units.remove(u);
                    }
                }
                if (leftOp instanceof JInstanceFieldRef) {
                    if (!FieldPrivatizedClasses.contains(((JInstanceFieldRef) leftOp).getField().getDeclaringClass())) continue;
                    JInstanceFieldRef fieldRef = (JInstanceFieldRef) leftOp;
                    Local referredObj = (Local) fieldRef.getBase();
                    SootField field = fieldRef.getField();
                    SootMethod setter = field.getDeclaringClass().getMethodByName("set" + capitalizeFirstLetter(field.getName()));
                    if (setter != null) {
                        Unit newUnit = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(referredObj, setter.makeRef(), stmt.getRightOp()));
                        units.insertBefore(newUnit, u);
                        units.remove(u);
                    }
                }
            }
        }
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
