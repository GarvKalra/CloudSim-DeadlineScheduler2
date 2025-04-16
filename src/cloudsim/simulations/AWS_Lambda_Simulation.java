package cloudsim.simulations;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import java.util.*;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;

import java.text.DecimalFormat;
import java.util.*;

public class AWS_Lambda_Simulation {
    
    // Warm start duration in seconds
    public static void main(String[] args) throws Exception {
        int numUsers = 1;
        Calendar calendar = Calendar.getInstance();
        boolean traceFlag = false;

        CloudSim.init(numUsers, calendar, traceFlag);

        Datacenter datacenter = createDatacenter("Datacenter_1");

        DeadlineAwareBroker broker = new DeadlineAwareBroker("DeadlineBroker");
        List<Vm> vmlist = new ArrayList<>();
        List<Cloudlet> cloudletList = new ArrayList<>();

        // VMs
        for (int i = 0; i < 2; i++) {
            Vm vm = new Vm(i, broker.getId(), 1000, 1, 2048, 1000, 10000,
                    "Xen", new CloudletSchedulerTimeShared());
            vmlist.add(vm);
        }

        // Deadline Cloudlets
        for (int i = 0; i < 5; i++) {
            DeadlineCloudlet cloudlet = new DeadlineCloudlet(
                    i, 10000, 1, 300, 300,
                    new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull(),
                    50 + i * 10 // Assigning increasing deadlines
            );
            cloudlet.setUserId(broker.getId());
            cloudletList.add(cloudlet);
        }

        // Sort cloudlets by deadlines (earliest deadline first)
        cloudletList.sort(Comparator.comparingDouble(c -> ((DeadlineCloudlet) c).getDeadline()));

        broker.submitVmList(vmlist);
        broker.submitCloudletList(cloudletList);

        // Enhanced logic for assigning cloudlets based on VM load and deadlines
        assignCloudletsToVMs(broker, cloudletList, vmlist);

        CloudSim.startSimulation();

        List<Cloudlet> newList = broker.getCloudletReceivedList();
        CloudSim.stopSimulation();

        System.out.println("\n========== Results ==========");
        for (Cloudlet cloudlet : newList) {
            DeadlineCloudlet dCloudlet = (DeadlineCloudlet) cloudlet;
            double deadline = dCloudlet.getDeadline();
            double finishTime = dCloudlet.getFinishTime();
            boolean metDeadline = finishTime <= deadline;

            System.out.println("Cloudlet ID: " + dCloudlet.getCloudletId()
                    + " | VM: " + dCloudlet.getVmId()
                    + " | Status: " + dCloudlet.getStatus()
                    + " | Deadline: " + deadline
                    + " | Finish Time: " + finishTime
                    + " | " + (metDeadline ? "✅ Met Deadline" : "❌ Missed Deadline"));}
    }

    private static void assignCloudletsToVMs(DeadlineAwareBroker broker, List<Cloudlet> cloudletList, List<Vm> vmlist) {
        // VM load tracking (how many cloudlets assigned to each VM)
        Map<Integer, List<Cloudlet>> vmLoads = new HashMap<>();
        for (Vm vm : vmlist) {
            vmLoads.put(vm.getId(), new ArrayList<>());
        }

        // Assign cloudlets based on deadlines and VM load
        for (Cloudlet cloudlet : cloudletList) {
            Vm selectedVm = null;
            long earliestFinishTime = Long.MAX_VALUE;

            // Find the VM with least load and earliest finish time
            for (Vm vm : vmlist) {
                List<Cloudlet> assignedCloudlets = vmLoads.get(vm.getId());
                long vmFinishTime = getEstimatedFinishTime(assignedCloudlets, cloudlet, vm);
                // Choose VM with the earliest finish time and least load
                if (vmFinishTime < earliestFinishTime) {
                    earliestFinishTime = vmFinishTime;
                    selectedVm = vm;
                }
            }

            // Assign cloudlet to the selected VM
            if (selectedVm != null) {
                vmLoads.get(selectedVm.getId()).add(cloudlet);
                broker.bindCloudletToVm(cloudlet.getCloudletId(), selectedVm.getId());
            }
        }
    }

    private static long getEstimatedFinishTime(List<Cloudlet> assignedCloudlets, Cloudlet newCloudlet, Vm vm) {
        long totalLength = 0;
        for (Cloudlet cl : assignedCloudlets) {
            totalLength += cl.getCloudletLength();
        }

        // Add the new cloudlet length
        totalLength += newCloudlet.getCloudletLength();

        // Estimate time = total instructions / MIPS
        return (long) (totalLength / vm.getMips());
    }

    private static Datacenter createDatacenter(String name) throws Exception {
        List<Host> hostList = new ArrayList<>();

        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerSimple(2000))); // MIPS

        Host host = new Host(
                0,
                new RamProvisionerSimple(4096),
                new BwProvisionerSimple(10000),
                1000000,
                peList,
                new VmSchedulerTimeShared(peList)
        );

        hostList.add(host);

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                "x86", "Linux", "Xen", hostList,
                10.0, 3, 0.05, 0.1, 0.1);

        return new Datacenter(name, characteristics,
                new VmAllocationPolicySimple(hostList),
                new LinkedList<Storage>(), 0);
    }
    
}