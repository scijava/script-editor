import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.connect.spi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.IOException;


public class VMLaunchExample {

	  public static String className = "Test";
	  public static String fieldName = "foo";
	   List arguments;
	   static int lineNumber;
	   static Field toKnow;
	   static ClassType refType;

	  public static void main(String[] args) throws IOException, InterruptedException,AbsentInformationException {

			VirtualMachine vm = launchVirtualMachine();
			addClassWatch(vm);

			// resume the vm
			vm.resume();

			// process events
			EventQueue eventQueue = vm.eventQueue();
			while (true) {
			  EventSet eventSet = eventQueue.remove();
			  for (Event event : eventSet) {
				if (event instanceof VMDeathEvent
					|| event instanceof VMDisconnectEvent) {
				  // exit
				  return;
				} else if (event instanceof ClassPrepareEvent) {
					  // watch field on loaded class
					ClassPrepareEvent classPrepEvent = (ClassPrepareEvent) event;
					refType = (ClassType)classPrepEvent.referenceType();
					toKnow=refType.fieldByName(fieldName);
					lineNumber=15;
					addBreakPointRequest(vm,refType,lineNumber);
				}
				else if(event instanceof BreakpointEvent) {
					BreakpointEvent breakEvent=(BreakpointEvent)event;
					System.out.println(refType.getValue(toKnow));
				}
			}
			eventSet.resume();
			}
	  }

	private static VirtualMachine launchVirtualMachine() {
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
		mainarg.setValue("Test dubey");
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
	  private static void addClassWatch(VirtualMachine vm) {
		EventRequestManager erm = vm.eventRequestManager();
		ClassPrepareRequest classPrepareRequest = erm
			.createClassPrepareRequest();
		classPrepareRequest.addClassFilter(className);
		classPrepareRequest.setEnabled(true);
	  }

  /** Watch field of name "foo" */
	  private static void addFieldWatch(VirtualMachine vm,
		  ReferenceType refType) {
			EventRequestManager erm = vm.eventRequestManager();
			Field field = refType.fieldByName(fieldName);
			ModificationWatchpointRequest modificationWatchpointRequest = erm
				.createModificationWatchpointRequest(field);
			modificationWatchpointRequest.setEnabled(true);
	  }
  
	public static void addBreakPointRequest(VirtualMachine vm,ReferenceType refType,int lineNumber) throws AbsentInformationException{

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




