package fiji.scripting;
//import ij.IJ;
//import ij.Menus;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.Iterator;
//import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.lang.Object;
import java.awt.List;

	/****This class generates and prints the 
	list of trees having each part of the classnames path 
	as a node of the tree like for java.awt.List ,the top level List
	contains a Tree object with key "java" and one of its childList
	element as awt which in turn has its childDList having its one childList
	as Listwhich is infact also a leaf ***********/










class ClassNames {

	static List list=new List();
	static sortedSet topLevel=new sortedSet();
	static sortedSet lowestLevel=new sortedSet();
	static Tree latest=new Tree();
	static Tree deflatest=new Tree();
	static sortedSet listtry=new sortedSet();
	//static String names[]={};



	public void run(String[] args) {
		run(args, "false");
		//return(lowestLevel);
	}

	public void run(String[] args, String verbose) {

		//ClassNames checker = new ClassNames();

		for (int i = 1; i < args.length; i++){
			latest=null;
			getClassNames(args[i]);
			 			//to nullify the latest pointer of search before every path in the classpath
		}

		createTreeList(listtry);										//calling the function to create the tree with root as the topLevel list and a reference to the lowestLevel lists

	}

	public sortedSet getTopLevel() {
		return topLevel;
	}

	public sortedSet lowestLevel() {
		return lowestLevel;
	}

	/*this function adds every class name (with its path )
	to a arraylist which is further converted to a
	String array*/
	public void getClassNames(String path){
		File file = new File(path);
		if (file.isDirectory()) {
			if (!path.endsWith(File.separator))
				path += File.separator;
			String[] list = file.list();
			for (int i = 0; i < list.length; i++)
				getClassNames(path + list[i]);               //recursively adding the classnames to the list
		}
		if(path.endsWith(".jar")) {
			try {

				ZipFile jarFile = new ZipFile(file);
				Enumeration list1 = jarFile.entries();
				while (list1.hasMoreElements()){
					ZipEntry entry =(ZipEntry)list1.nextElement();
						String name = entry.getName();
						if(!name.endsWith(".class"))		//ignoring the non class files
							continue;
						listtry.add(name);
				}
			}catch(Exception e){System.out.println("Invalid jar file");}
		}
		else if (path.endsWith(".class")) {
			listtry.add(path);
		}
	}

	/*as the name suggests it creates the list of trees
	with each tree having its root as the an element of 
	the top level list*/

	public void createTreeList(sortedSet names) {
		int latestIndex=0;
		int deflatestIndex=0;
		Tree temp;

				for(Object name1 : names){
					String name=(String)name1;

						//initial check for any other files other than classes just ignore them
						if (!name.endsWith(".class")){
							continue;
						}
							//counter++;

							String[] classname1=name.split("\\\\");
							String[] classname2=classname1[classname1.length-1].split("/");
							/*String fullname=classname1[classname1.length-1];
							int index=fullname.lastIndexOf("/");
							String packageName="";
							if(index>0) {
								packageName=fullname.substring(0,index);
							}
							String className=fullname.substring(index+1,fullname.length);
							if(!(packageSet.contains(packageName))){
								packageSet.add(new PackageItem(packageName,new);
							}*/

							String justClassname=classname2[classname2.length-1];
							if(classname2[0].endsWith(".class")){
								lowestLevel.add(new Tree(classname2[0]));
							}
							else if(latest==null){

								topLevel.add(new Tree(classname2[0]));
								setLinearBranch((Tree)topLevel.last(),classname2,1);

							}
							else{

								try{
								if(classname2.length>1){

									latestIndex=classname2.length-2;
									while(!(latest.key.equals(classname2[latestIndex]))){
										latestIndex--;
										//try{
										if(latest.parent==null)
											break;
										latest=latest.parent;
									}

									if(latest.parent==null){
										temp=(Tree)topLevel.last();
										if(temp.key.equals(classname2[0])){

												latestIndex = findRightLatest(temp,classname2,1);
												if(latestIndex==-1){
													latestIndex=deflatestIndex;
													latest=deflatest;
													continue;
												}
												setLinearBranch(latest,classname2,++latestIndex);

										}
										else{
											topLevel.add(new Tree(classname2[0]));
											setLinearBranch((Tree)topLevel.last(),classname2,1);
										}
									}
									else{
										setLinearBranch(latest,classname2,++latestIndex);
									}

								}
								}catch(NullPointerException e){}
							}




			deflatest=latest;
			deflatestIndex=latestIndex;
		}

		//return topLevel;
	}
	public void setLinearBranch(Tree currentRoot,String[] partsOfPackage,int index){



		if(index>partsOfPackage.length-1){
		}

		else if(index==partsOfPackage.length-1){
			currentRoot.childList.add(new Tree(partsOfPackage[index]));
			latest=currentRoot;
			//System.out.println(latest.key);
			latest.key=currentRoot.key;
			lowestLevel.add(currentRoot.childList.last());
		}

		else{

			currentRoot.childList.add(new Tree(partsOfPackage[index]));
			setLinearBranch((Tree)currentRoot.childList.last(),partsOfPackage,++index);

		}
	}

	public int findRightLatest(Tree tree1,String[] partsOfPackage,int index){
	try{
		Tree temp1=(Tree)tree1.childList.last();
		if(temp1.key.equals(partsOfPackage[index]))
			return(findRightLatest(temp1,partsOfPackage,++index));
		else{
			latest=tree1;
			return --index;
		}
	}catch(Exception e){
		return -1;
	}
	}



}




