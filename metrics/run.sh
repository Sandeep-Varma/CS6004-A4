
cd sootOutput

for dir in *
do
    echo $dir
    cd $dir
    (time ../../openj9-openjdk-jdk8/build/linux-x86_64-normal-server-release/images/j2sdk-image/bin/java -Xint Test) &> output.txt
    cd ..
done
cd ..
