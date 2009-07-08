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

  public static final String CLASS_NAME = "Test";
  public static final String FIELD_NAME = "foo";
   

  public static void main(String[] args) throws IOException, InterruptedException {
    // connect
	VirtualMachine vm = launchVirtualMachine();

    //VirtualMachine vm = v.connect(8000);

    // set watch field on already loaded classes
    List<ReferenceType> referenceTypes = vm
        .classesByName(CLASS_NAME);
    for (ReferenceType refType : referenceTypes) {
      addFieldWatch(vm, refType);
    }
    // watch for loaded classes
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
          ReferenceType refType = classPrepEvent
              .referenceType();
          addFieldWatch(vm, refType);
        } else if (event instanceof ModificationWatchpointEvent) {
          // a Test.foo has changed
          ModificationWatchpointEvent modEvent = (ModificationWatchpointEvent) event;
          System.out.println("old="
              + modEvent.valueCurrent());
          System.out.println("new=" + modEvent.valueToBe());
          System.out.println();
        }
      }
      eventSet.resume();
    }
  }

	private static VirtualMachine launchVirtualMachine() {
		VirtualMachineManager vmm=Bootstrap.virtualMachineManager();
		LaunchingConnector defConnector=vmm.defaultConnector();
		Map<String,Connector.Argument> arguments=defConnector.defaultArguments();
		Set<String> s = arguments.keySet();
		for(String string:s)
			System.out.println(string);
		Connector.Argument mainarg=arguments.get("main");
		mainarg.setValue("Test");
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
    classPrepareRequest.addClassFilter(CLASS_NAME);
    classPrepareRequest.setEnabled(true);
  }

  /** Watch field of name "foo" */
  private static void addFieldWatch(VirtualMachine vm,
      ReferenceType refType) {
    EventRequestManager erm = vm.eventRequestManager();
    Field field = refType.fieldByName(FIELD_NAME);
    ModificationWatchpointRequest modificationWatchpointRequest = erm
        .createModificationWatchpointRequest(field);
    modificationWatchpointRequest.setEnabled(true);
  }
}
