package fiji.scripting;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.connect.spi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import java.io.*;

public class StartDebugging {

	  public String className = "SamplePlugin";
	  public String fieldName="foo";
	  public String plugInName;
	   List arguments;
	    List lineNumbers;
	    Field toKnow;
	    ClassType refType;
		VirtualMachine vm;

		public StartDebugging(String plugin,List numbers) {
			plugInName=plugin;
			lineNumbers=numbers;
		}
	   
	   public StartDebugging(String plugin,String field,List numbers) {
			plugInName=plugin;
			fieldName=field;
			lineNumbers=numbers;
		}

		public Process startDebugging() throws IOException,InterruptedException,AbsentInformationException {

			vm = launchVirtualMachine();
			vm.resume();
			addClassWatch(vm);
			Process process= vm.process();
			InputStream inputStream=process.getErrorStream();
			EventQueue eventQueue = vm.eventQueue();

			while (true) {
			  EventSet eventSet = eventQueue.remove();
			  for (Event event : eventSet) {
				if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
					BufferedReader reader=new BufferedReader(new InputStreamReader(inputStream));
					System.out.println(reader.readLine());
				    return process;
				} else if (event instanceof ClassPrepareEvent) {
					System.out.println("It comes in the class prepare event");
					  // watch field on loaded class
					ClassPrepareEvent classPrepEvent = (ClassPrepareEvent) event;
					refType = (ClassType)classPrepEvent.referenceType();
					System.out.println("The class loaded is "+refType.name());
					toKnow=refType.fieldByName(fieldName);
					System.out.println("The number of elements in the linenumber list is"+lineNumbers.size());
					Iterator iterator=lineNumbers.iterator();
					while(true) {
						if(iterator.hasNext()) {
							Integer k=(Integer)iterator.next();
							System.out.println("The line number is "+k.intValue());
							addBreakPointRequest(vm,refType,k.intValue()+1);
						}
						else 
							break;
					}
				}
				else if(event instanceof BreakpointEvent) {
					BreakpointEvent breakEvent=(BreakpointEvent)event;
					System.out.println(refType.getValue(toKnow));
					//vm.suspend();
				}
				else if(event instanceof VMStartEvent) {
					System.out.println("Virtual machine started");
				}

			}
			eventSet.resume();
		}
	}

	public void resumeVM() {
		vm.resume();
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
		String s1=System.getProperty("java.class.path");
		mainarg.setValue("-classpath \""+s1+"\" fiji.MainClassForDebugging "+plugInName.substring(plugInName.lastIndexOf(File.separator)+1));

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
		return null;
	}

  /** Watch all classes of name "Test" */
	  private  void addClassWatch(VirtualMachine vm) {
		System.out.println(className);
		EventRequestManager erm = vm.eventRequestManager();
		ClassPrepareRequest classPrepareRequest = erm.createClassPrepareRequest();
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




