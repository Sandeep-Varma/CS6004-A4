import os

# for each directory in sootOutput directory
for dir in os.listdir("sootOutput"):
    print(dir)
    # read dir/output.txt
    lines = open("sootOutput/" + dir + "/output.txt").readlines()
    # filter lines starting with "Method Call: "
    method_calls = [line for line in lines if line.startswith("Method Call: ")]
    # print the number of method calls
    print(len(method_calls))
    # print last 3 lines
    print(lines[-3])