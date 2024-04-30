rm -f output.txt
rm -rf *.class */*.class testcase sootOutput

javac -cp .:soot.jar PA4.java

for file in testcases/*.java
do
    testcase=$(basename $file)
    mkdir testcase
    cp $file testcase/Test.java
    cd testcase/
    javac -g Test.java
    cd ..
    echo "####################################################################### $testcase"
    java -cp .:soot.jar PA4 $file no_op
    java -cp .:soot.jar PA4 $file op
    #  | grep -v "^Soot " >> ../output.txt
    rm -rf testcase
done

rm -rf *.class testcases/*.class
rm -rf metrics/sootOutput
cp -r sootOutput metrics/
