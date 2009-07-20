package fiji.scripting;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.connect.spi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.*;




public class StartDebugging {

	  public String className = "fiji.scripting.MainClassForDebugging";
	  public String fieldName="foo";
	  public String plugInName;
	   List arguments;
	    int lineNumber;
	    Field toKnow;
	    ClassType refType;

		public StartDebugging(String plugin) {
			plugInName=plugin;
		}
	   
	   public StartDebugging(String plugin,String field,int line) {
			plugInName=plugin;
			fieldName=field;
			lineNumber=line;
		}

		public Process startDebugging() throws IOException,InterruptedException,AbsentInformationException {
			String s=System.getProperty("java.class.path");
			String p=File.separator;
			String s1=System.setProperty("java.class.path",s+File.pathSeparator+"c:"+p+"DOCUME~1"+p+"Sumi"+p+"fiji"+p+"plugins"+p+p+"Script_Editor.jar");
			VirtualMachine vm = launchVirtualMachine();
			String s2=System.setProperty("java.class.path",s);
			System.out.println(s2);
			vm.resume();
			addClassWatch(vm);
			Process process= vm.process();

			InputStream inputStream=process.getErrorStream();

			EventQueue eventQueue = vm.eventQueue();

			while (true) {
			  EventSet eventSet = eventQueue.remove();
			  System.out.println("It comes in while");
			  for (Event event : eventSet) {
				System.out.println("It comes here");
				if (event instanceof VMDeathEvent
					|| event instanceof VMDisconnectEvent) {
					BufferedReader reader=new BufferedReader(new InputStreamReader(inputStream));
					System.out.println(reader.readLine());
				  // exit
				  //out.flush();
				  System.out.println(process.exitValue());
				  return process;
				} else if (event instanceof ClassPrepareEvent) {
					System.out.println("It comes in the class prepare event");
					  // watch field on loaded class
					ClassPrepareEvent classPrepEvent = (ClassPrepareEvent) event;
					refType = (ClassType)classPrepEvent.referenceType();
					System.out.println("The class loaded is "+refType.name());
					toKnow=refType.fieldByName(fieldName);
					lineNumber=15;
					addBreakPointRequest(vm,refType,lineNumber);
				}
				else if(event instanceof BreakpointEvent) {
					BreakpointEvent breakEvent=(BreakpointEvent)event;
					System.out.println(refType.getValue(toKnow));
				}
				else if(event instanceof VMStartEvent) {
					System.out.println("The event is VMStartEvent of their type");

				}
			}
			eventSet.resume();
		}
	}

	private  VirtualMachine launchVirtualMachine() {

		VirtualMachineManager vmm=Bootstrap.virtualMachineManager();
		LaunchingConnector defConnector=vmm.defaultConnector();
		Transport transport=defConnector.transport();
		List<LaunchingConnector> list=vmm.launchingConnectors();
		for(LaunchingConnector conn: list)
			System.out.println(conn.name());
		Map<String,Connector.Argument> arguments=defConnector.defaultArguments();
		Set<String> s = arguments.keySet();
		for(String string:s)
			System.out.println(string);
		Connector.Argument mainarg=arguments.get("main");
		mainarg.setValue("fiji.scripting.MainClassForDebugging plugins/SamplePlugin.java");
		try {
			return defConnector.launch(arguments);

		} catch (IOException exc) {
			throw new Error("Unable to launch target VM: " + exc);
		} catch (IllegalConnectorArgumentsException exc) {
			exc.printStackTrace();
		} catch (VMStartException exc) {
			throw new Error("Target VM failed to initialize: " +
			exc.getMessage());
		} 
		//System.out.println("Not launched");
		return null;
	}

  /** Watch all classes of name "Test" */
	  private  void addClassWatch(VirtualMachine vm) {
		System.out.println(className);
		EventRequestManager erm = vm.eventRequestManager();
		ClassPrepareRequest classPrepareRequest = erm
			.createClassPrepareRequest();
		classPrepareRequest.addClassFilter(className);
		classPrepareRequest.setEnabled(true);
	  }

  
  
	public  void addBreakPointRequest(VirtualMachine vm,ReferenceType refType,int lineNumber) throws AbsentInformationException{

		List listOfLocations=refType.locationsOfLine(lineNumber);
		if (listOfLocations.size() == 0) {
			System.out.println("No element in the list of locations ");
			return;
		}
		Location loc=(Location)listOfLocations.get(0);
		EventRequestManager erm = vm.eventRequestManager();
		BreakpointRequest breakpointRequest=erm.createBreakpointRequest(loc);
		breakpointRequest.setEnabled(true);
	}

}




