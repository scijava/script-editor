import ij.IJ;
import ij.Menus;
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
	
public class autocompleteClassGenerator{
	
	
	
	public static void main(String args[]){
		lists toPrint=generate();
		/*for(int i=0;i<toPrint.length;i++){
			System.out.println(toPrint[i]);
		}*/
		printList(toPrint);
	}		
	
		
	/*this function returns the list of topLevel Lists of the classnames 
	with their childlren linked to them*/
	public static lists generate(){
		String paths[]=System.getProperty("java.class.path").split(File.pathSeparator);
		return(ClassNames.run(paths));
	}
	
	/*printling the tree*/
	public static void printList(lists list){
		for(int i=0;i<list.size();i++){
			Tree tree=(Tree)list.get(i);
			if(tree.childList.size()>0)
				printList(tree.childList);
				System.out.println(tree.key);
		}
			
		
	}	
}	
		
		




class ClassNames {
	
	static List list=new List();
	static lists topLevel=new lists();
	static lists lowestLevel=new lists();
	static Tree latest=new Tree();
	static Tree deflatest=new Tree();
	static java.util.List listtry=new ArrayList();
	static String names[]={};

	

	public static lists run(String[] args) {
		run(args, "false");
		return(topLevel);
	}

	public static void run(String[] args, String verbose) {
		
		ClassNames checker = new ClassNames();
		
		for (int i = 1; i < args.length; i++){
			latest=null;
			checker.getClassNames(args[i]);
			 			//to nullify the latest pointer of search before every path in the classpath
		}
		names = (String[])listtry.toArray(new String[0]);                  //generate the names of all the classes with their complete path 
		names=merge_sort(names);											//sorting the array containing the names of the classes
		checker.createTreeList(names);										//calling the function to create the tree with root as the topLevel list and a reference to the lowestLevel lists
		
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
	
	public void createTreeList(String[] names) {
		int latestIndex=0;
		int deflatestIndex=0;
		Tree temp;
		
				for(String name : names){
						//initial check for any other files other than classes just ignore them
						if (!name.endsWith(".class")){
							continue;
						}	
							//counter++;
							
							String[] classname1=name.split("\\\\");
							String[] classname2=classname1[classname1.length-1].split("/");
							String justClassname=classname2[classname2.length-1];
							if(classname2[0].endsWith(".class")){
								lowestLevel.add(new Tree(classname2[0]));
							}	
							else if(latest==null){
								
								topLevel.add(new Tree(classname2[0]));
								setLinearBranch((Tree)topLevel.get(topLevel.size()-1),classname2,1);
								
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
										temp=(Tree)topLevel.get(topLevel.size()-1);
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
											setLinearBranch((Tree)topLevel.get(topLevel.size()-1),classname2,1);
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
			lowestLevel.add(currentRoot.childList.get(currentRoot.childList.size()-1));
		}
		
		else{
			
			currentRoot.childList.add(new Tree(partsOfPackage[index]));
			setLinearBranch((Tree)currentRoot.childList.get(currentRoot.childList.size()-1),partsOfPackage,++index);
			
		}
	}

	public int findRightLatest(Tree tree1,String[] partsOfPackage,int index){
	try{
		Tree temp1=(Tree)tree1.childList.get(tree1.childList.size()-1);
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
	public static String[] merge_sort(String[] m){
		String[] left=new String[m.length/2];
		String[] right=new String[m.length%2==0?m.length/2:(m.length/2)+1];
		String[] result=new String[m.length];
		if (m.length <= 1)
			return m;

		// This calculation is for 1-based arrays. For 0-based, use length(m)/2 - 1.
		int middle = m.length/2;
		for(int i=0;i<middle;i++){
		
			left[i]=m[i];
			right[i]=m[middle+i];
			
		}
		if(m.length%2!=0)
			right[middle]=m[m.length-1];
		
		left = merge_sort(left);
		right = merge_sort(right);
		if (left[(m.length/2)-1].compareTo(right[0])>0)
			 result = merge(left,right);
		else{
			for(int i=0;i<result.length;i++){
				if(i<left.length)
					result[i]=left[i];
				else
					result[i]=right[i-left.length];
			}		
			 //append(left,right);
			 //result=left;
		}	 
		return result;
	}
	public static String[] merge(String[] left,String[] right){
		String[] result=new String[left.length+right.length];
		int counter=0;
		while( left.length>0 && right.length>0){
			if (left[0].compareTo(right[0])<=0){
				result[counter]=left[0];
				counter++;
				String[] temp=new String[left.length-1];
				for(int i=0;i<left.length-1;i++){
					temp[i] = left[i+1];
				}
				left=temp;
			}	
			else{
				result[counter]=right[0];
				counter++;
				String[] temp=new String[right.length-1];
				for(int i=0;i<right.length-1;i++){
					temp[i] = right[i+1];
				}
				right=temp;
			}	
		}
		if(left.length > 0){
			for(int i=0;i<left.length;i++)
				result[counter+i]=left[i];
			//append(result,left);
		}	
		if(right.length > 0) {
			for(int i=0;i<right.length;i++)
				result[counter+i]=right[i];
			//append(result,right);
		}	
		return result;
	}


	
}

class Tree extends Object{
	String key;
	lists childList;
	Tree parent;
	
	public Tree(){
		this(null);
	}
	
	
	public Tree(String key){
		this.key=key;
		this.childList=new lists();
	}

	//this constructor is used for all other tree objects other than leaves which have non null childList and whose parent is not known or does not exist like the root
	public Tree(String key,lists childList){
		this.key=key;
		this.childList=childList;
	}

	public Tree(String key,Tree parent){
		this.key=key;
		this.parent=parent;
	}

	public Tree(String key,lists childList,Tree parent){
		this.key=key;
		this.childList=childList;
		this.parent=parent;
	}
}

class lists extends ArrayList{
	public lists(){
		//this=null;
	}
	//public void add(Tree tree)
	/*public get(int index){
	}*/
	
}			
