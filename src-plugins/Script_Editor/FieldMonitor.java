import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.connect.spi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.util.List;
import java.util.Map;
import java.io.IOException;


public class FieldMonitor {

  public static final String CLASS_NAME = "Test";
  public static final String FIELD_NAME = "foo";
  static VMAcquirer v=new VMAcquirer();

  public static void main(String[] args) throws IOException, InterruptedException {
    // connect

    VirtualMachine vm = v.connect(8000);

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

static class VMAcquirer {

	public VMAcquirer() {
	}
  /**
   * Call this with the localhost port to connect to.
   */
  public VirtualMachine connect(int port)
      throws IOException {
	System.out.println("here");
    String strPort = Integer.toString(port);
    AttachingConnector connector = getConnector();
    try {
      VirtualMachine vm = connect(connector, strPort);
      return vm;
    } catch (IllegalConnectorArgumentsException e) {
      throw new IllegalStateException(e);
    }
  }

  private AttachingConnector getConnector() {
    VirtualMachineManager vmManager = Bootstrap
        .virtualMachineManager();
    for (Connector connector : vmManager
        .attachingConnectors()) {
      System.out.println(connector.name());
      if ("com.sun.jdi.SocketAttach".equals(connector
          .name())) {
        return (AttachingConnector) connector;
      }
    }
    throw new IllegalStateException();
  }

  private VirtualMachine connect(
      AttachingConnector connector, String port)
      throws IllegalConnectorArgumentsException,
      IOException {
    Map<String, Connector.Argument> args = connector
        .defaultArguments();
    Connector.Argument pidArgument = args.get("port");
    if (pidArgument == null) {
      throw new IllegalStateException();
    }
    pidArgument.setValue(port);

    return connector.attach(args);
  }

}

}
