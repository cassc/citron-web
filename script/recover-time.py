# -*- coding: utf-8 -*-
import re
import time



html_file = "/home/garfield/projects/pic/pic-web/resources/public/index.html"

def replace_src(file_name):
    # Read in the file
    with open(file_name, 'r') as file :
        filedata = file.read()

    filedata = re.sub(r"nonce=\d+\.?\d*", "nonce=201210101010", filedata, flags=re.MULTILINE)
    filedata = re.sub("prod-main.js", "dev-main.js", filedata)

    # Write the file out again
    with open(file_name, 'w') as file:
        file.write(filedata)


if __name__ == "__main__":
    replace_src(html_file)
