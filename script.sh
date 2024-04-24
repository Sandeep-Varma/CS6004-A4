rm -f output.txt
rm -rf *.class */*.class testcase sootOutput

javac -cp .:soot.jar PA4.java

for file in testcases/*.java
do
    mkdir testcase
    cp $file testcase/Test.java
    cd testcase/
    javac -g Test.java
    cd ..
    echo "####################################################################### $file"
    java -cp .:soot.jar PA4 $file
    #  | grep -v "^Soot " >> ../output.txt
    rm -rf testcase
done

rm -rf *.class testcases/*.class