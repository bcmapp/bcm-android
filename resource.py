import os
#import shutil
import zipfile

valid_module_list = ['adhoc-client','common','ame', 'me','chats','wallet','framework','contacts','app']
valid_dir_list = ['src','main','res','values-zh-rCN','values','strings.xml']

def zip_file_in_dir(filename,fromDir,toDir, zipf):
    
    for i in [d for d in os.listdir(fromDir) if d in valid_dir_list or d in valid_module_list]:
        t=os.path.join(fromDir,i)
        if os.path.isfile(t) and filename in os.path.split(t)[1]:
            #ori = os.path.split(os.path.relpath(x))
            #dest = os.path.join(toDir, ori[0])
            #if not os.path.isdir(dest):
            #    os.makedirs(dest)
            #shutil.copyfile(x, os.path.join(dest, ori[1]))
            zipf.write(t, os.path.relpath(t))
            print('zip: '+os.path.relpath(t))
        elif os.path.isdir(t):
            zip_file_in_dir(filename, t, toDir, zipf)

rp = os.path.abspath('.')
toDir = os.path.join(rp,'resources')
#if os.path.isdir(toDir):
#    shutil.rmtree(toDir)
#os.mkdir(toDir)

resource_file = os.path.join(rp, 'resources.zip')
if os.path.isfile(resource_file):
    os.remove(resource_file)
zipf = zipfile.ZipFile(os.path.join(rp, 'resources.zip'), 'w') 
zip_file_in_dir("strings.xml", rp, toDir, zipf)
zipf.close()