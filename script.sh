rm -f output.txt
rm -rf *.class */*.class testcase sootOutput javaOutput

javac -cp .:soot.jar PA4.java

mkdir javaOutput

for file in testcases/*.java
do
    testcase=$(basename $file)
    mkdir testcase
    cp $file testcase/Test.java
    cd testcase/
    javac -g Test.java
    cd ..
    mkdir javaOutput/${testcase%.*}
    cp testcase/*.class javaOutput/${testcase%.*}/
    echo "####################################################################### $testcase"
    java -cp .:soot.jar PA4 $file
    #  | grep -v "^Soot " >> ../output.txt
    rm -rf testcase
done

rm -rf *.class testcases/*.class