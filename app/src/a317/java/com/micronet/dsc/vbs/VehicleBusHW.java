/*
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */

//////////////////////////////////////////////////////////////////
// This contains the normalization between different HW API implementations
//////////////////////////////////////////////////////////////////

package com.micronet.dsc.vbs;


import com.micronet.canbus.CanbusHardwareFilter;
import com.micronet.canbus.CanbusInterface;
import com.micronet.canbus.CanbusInterfaceInformation;
import com.micronet.canbus.CanbusSocket;
import com.micronet.canbus.GetInterfaceInformation;
import com.micronet.canbus.J1708InterfaceInformation;

class VehicleBusHW {
    public static final String TAG = "ATS-VBS-HW";



    public static class InterfaceWrapper extends CanbusInterface {
        public CanbusInterface canbusInterface;

        public InterfaceWrapper(CanbusInterface i) {
            canbusInterface = i;
        }
    }

    public static class SocketWrapper {
        public CanbusSocket canbusSocket;

        public SocketWrapper(CanbusSocket s) {
            canbusSocket = s;
        }
    }


    ///////////////////////////////////////////////////////////
    // Intermediate Access Layer:
    //  Wrappers for classes/methods in canbus library that will be needed by other classes
    ///////////////////////////////////////////////////////////
    public static class CANFrame extends com.micronet.canbus.CanbusFrame {
        public CANFrame(int id, byte[] data, CANFrameType type) {
            super(id, data, CANFrameType.upcast(type));
        }

        public static CANFrame downcast(com.micronet.canbus.CanbusFrame mFrame) {
            return new CANFrame(mFrame.getId(), mFrame.getData(), CANFrameType.downcast(mFrame.getType()));
        }

        public int getId() {
            return super.getId();
        }

        public byte[] getData() {
            return super.getData();
        }

    };

    public static class CANSocket {

        com.micronet.canbus.CanbusSocket socket;

        public CANSocket(SocketWrapper in) {
            if (in != null)
                socket = in.canbusSocket;
            else socket = null;
        }

        public CANFrame read() {
            return CANFrame.downcast(socket.read());
        }

        public void write(CANFrame frame) {
            socket.write(frame);
        }
    } // CANSocket

    public enum CANFrameType {
        EXTENDED,
        STANDARD;

        public static CANFrameType downcast(com.micronet.canbus.CanbusFrameType mFrame) {
            if (mFrame == com.micronet.canbus.CanbusFrameType.EXTENDED) return EXTENDED;
            return STANDARD;
        }

        public static com.micronet.canbus.CanbusFrameType upcast(CANFrameType frame) {
            if (frame == EXTENDED) return com.micronet.canbus.CanbusFrameType.EXTENDED;
            return com.micronet.canbus.CanbusFrameType.STANDARD;
        }
    }

    public static class CANHardwareFilter extends com.micronet.canbus.CanbusHardwareFilter {

        public CANHardwareFilter(int[] ids, int mask, VehicleBusWrapper.CANFrameType type) {
            super(ids, mask, CANFrameType.upcast(type));
        }
    };








    public static class J1708Frame extends com.micronet.canbus.J1708Frame {
        public J1708Frame(int priority, int id, byte[] data) {
            super(priority, id, data);
        }

        public static J1708Frame downcast(com.micronet.canbus.J1708Frame mFrame) {
            return new J1708Frame(mFrame.getPriority(), mFrame.getId(), mFrame.getData());
        }

    };

    public static class J1708Socket {

        com.micronet.canbus.CanbusSocket socket;

        public J1708Socket(SocketWrapper in) {
            if (in != null)
                socket = in.canbusSocket;
            else socket = null;
        }

        public J1708Frame readJ1708() {
            return J1708Frame.downcast(socket.readJ1708());
        }

        public void writeJ1708(J1708Frame frame) {
            socket.writeJ1708(frame);
        }

    } // J1708Socket()







    ///////////////////////////////////////////////////////////
    // Internal Access Layer:
    //  Wrappers for classes/methods in canbus library that will be needed only by VehicleBusWrapper class
    ///////////////////////////////////////////////////////////








    InterfaceWrapper createInterface(boolean listen_only, int bitrate, CANHardwareFilter[] hardwareFilters) {

        CanbusInterface canInterface = null;

        try {
            canInterface = new CanbusInterface();
        } catch (Exception e) {
            Log.e(TAG, "Unable to create new CanbusInterface() " + e.toString());
            return null;
        }

        // We must set bitrate and listening mode both before and after creating the interface.
        // We would prefer to always set before, but that doesn't always work

        try {
            canInterface.setBitrate(bitrate);
        } catch (Exception e) {
            Log.e(TAG, "Unable to set bitrate for CanbusInterface() " + e.toString());
            return null;
        }



        // we must first set listening only mode before creating it as listen-only
        try {
            canInterface.setListeningMode(listen_only);
        } catch (Exception e) {
            Log.e(TAG, "Unable to set mode for CanbusInterface() " + e.toString());
            return null;
        }

        try {
            canInterface.create(listen_only);
        } catch (Exception e) {
            Log.e(TAG, "Unable to call create(" + listen_only + ") for CanbusInterface() " + e.toString());
            return null;
        }


        try {
            canInterface.setListeningMode(listen_only);
        } catch (Exception e) {
            Log.e(TAG, "Unable to set mode for CanbusInterface() " + e.toString());
            return null;
        }


        // Set the bitrate again since it doesn't work to set this before creating interface first time after power-up
        // We are in listen mode, so it shouldn't be a problem to open at wrong bitrate
        try {
            canInterface.setBitrate(bitrate);
        } catch (Exception e) {
            Log.e(TAG, "Unable to set bitrate for CanbusInterface() " + e.toString());
            return null;
        }




        Log.d(TAG, "Interface created @ " + bitrate + "kb " + (listen_only ? "READ-ONLY" : "READ-WRITE"));

        if (hardwareFilters != null) {
            try {
                canInterface.setFilters(hardwareFilters);
            } catch (Exception e) {
                Log.e(TAG, "Unable to set filters for CanbusInterface() " + e.toString());
                try {
                    canInterface.remove();
                } catch (Exception e2) {
                    Log.e(TAG, "Unable to remove CanbusInterface() " + e2.toString());
                }
                return null;
            }
            String filter_str = "";
            for (CanbusHardwareFilter filter : hardwareFilters) {
                int[] ids = filter.getIds();
                filter_str += " (";
                for (int id : ids) {
                    filter_str += "x" + String.format("%X", id) + " ";
                }
                filter_str += "M:x" + String.format("%X", filter.getMask()) + ")";
            }

            Log.d(TAG, "Filters = " + filter_str);
        }

        try{
            getCanInfo(canInterface);
        } catch (Exception e){
            Log.e(TAG, "Unable to get CAN debug info");
        }


        return new InterfaceWrapper(canInterface);
    } // createInterface()

    public void getCanInfo(CanbusInterface canbusInterface) {
        GetInterfaceInformation getInterfaceInformation;
        int returnCode = -1;
        if (canbusInterface == null) {
            canbusInterface = new CanbusInterface();
            getInterfaceInformation = canbusInterface.getQBridgeCanInfo();
            returnCode = getInterfaceInformation.getReturnCodeRxd();
            android.util.Log.d(TAG, "Canbus Info Returned, Return code=" + returnCode);

        }
        else getInterfaceInformation = canbusInterface.getQBridgeCanInfo();

        android.util.Log.d(TAG, "Info Returned: TX count: " +  getInterfaceInformation.getQbCanInformation().getTxCount());
        android.util.Log.d(TAG, "Info Returned: RX Count: " +  getInterfaceInformation.getQbCanInformation().getRxCount());
        android.util.Log.d(TAG, "Info Returned: Stuff Error Count: " +  getInterfaceInformation.getQbCanInformation().getStuffErrorCount());
        android.util.Log.d(TAG, "Info Returned: Form Error Count: " +  getInterfaceInformation.getQbCanInformation().getFormErrorCount());
        android.util.Log.d(TAG, "Info Returned: Ack Error Count: " +  getInterfaceInformation.getQbCanInformation().getAckErrorCount());
        android.util.Log.d(TAG, "Info Returned: Bit 1 Error Count: " +  getInterfaceInformation.getQbCanInformation().getBit1ErrorCount());
        android.util.Log.d(TAG, "Info Returned: Bit 0 Error Count: " +  getInterfaceInformation.getQbCanInformation().getBit0ErrorCount());
        android.util.Log.d(TAG, "Info Returned: CRC Error Count: " +  getInterfaceInformation.getQbCanInformation().getCrcErrorCount());
        android.util.Log.d(TAG, "Info Returned: Lost Message Count: " +  getInterfaceInformation.getQbCanInformation().getLostMessageCount());
        android.util.Log.d(TAG, "Info Returned: CAN Rx interrupt queue overflowed: " +  getInterfaceInformation.getQbCanInformation().getCanRxInterruptQueueOverflowed());
        android.util.Log.d(TAG, "Info Returned: CAN Rx interrupt queue overflowed count: " +  getInterfaceInformation.getQbCanInformation().getCanRxInterruptQueueOverflowedCount());
        android.util.Log.d(TAG, "Info Returned: CAN transmit confirm: " +  getInterfaceInformation.getQbCanInformation().getCanTransmitConfirm());
        android.util.Log.d(TAG, "Info Returned: CAN Bus Off Notify: " +  getInterfaceInformation.getQbCanInformation().getCanBusOffNotify());
        android.util.Log.d(TAG, "Info Returned: CAN Auto Restart: " +  getInterfaceInformation.getQbCanInformation().getCanAutoRestart());
        android.util.Log.d(TAG, "Info Returned: CAN Auto Recovery Attempt Count: " +  getInterfaceInformation.getQbCanInformation().getCanAutoRecoveryAttemptCount());
        android.util.Log.d(TAG, "Info Returned: CAN Rx Wait for host count: " +  getInterfaceInformation.getQbCanInformation().getCanRxWaitForHostCount());
        android.util.Log.d(TAG, "Info Returned: CAN Rx Bad Value Count: " +  getInterfaceInformation.getQbCanInformation().getcanRxBadValueCount());
        android.util.Log.d(TAG, "Info Returned: bus off interrupt count: " +  getInterfaceInformation.getQbCanInformation().getBusOffInterruptCount());
        android.util.Log.d(TAG, "Info Returned: bus off notify count: " +  getInterfaceInformation.getQbCanInformation().getBusOffNotifyCount());
        android.util.Log.d(TAG, "Info Returned: bus off want to notify host count: " +  getInterfaceInformation.getQbCanInformation().getBusOffWantToNotifyHostCount());
        android.util.Log.d(TAG, "Info Returned: CAN bus error count: " +  getInterfaceInformation.getQbCanInformation().getCanbusErrorCount());
        android.util.Log.d(TAG, "Info Returned: CAN dropped from host count: " +  getInterfaceInformation.getQbCanInformation().getcanDroppedFromHostCount());
        android.util.Log.d(TAG, "Info Returned: CAN Bus transition detected since last time this command was executed: " +  getInterfaceInformation.getQbCanInformation().getCanbusTransitionDetected());
        android.util.Log.d(TAG, "Info Returned: CAN Current Bus State: " +  getInterfaceInformation.getQbCanInformation().getCanCurrentBusState());
        android.util.Log.d(TAG, "Info Returned: CAN Free Tx Buffers: " +  getInterfaceInformation.getQbCanInformation().getCanFreeTxBuffers());
        android.util.Log.d(TAG, "Info Returned: CAN Free Rx Buffers: " +  getInterfaceInformation.getQbCanInformation().getCanFreeRxBuffers());
        android.util.Log.d(TAG, "Info Returned: CAN Baud Rate: " +  getInterfaceInformation.getQbCanInformation().getCanBaudRate());
        android.util.Log.d(TAG, "Info Returned: CAN test mode: " +  getInterfaceInformation.getQbCanInformation().getCanTestMode());
        android.util.Log.d(TAG, "Info Returned: CAN Hardware error counters: " +  getInterfaceInformation.getQbCanInformation().getCanHardwareErrorCount());
        android.util.Log.d(TAG, "Info Returned: CAN overflow reported: " +  getInterfaceInformation.getQbCanInformation().getCanOverFlowReported());
    }

    public void getJ1708Info(CanbusInterface canbusInterface){
        GetInterfaceInformation getInterfaceInformation;
        int returnCode = -1;
        if (canbusInterface == null) {
            canbusInterface = new CanbusInterface();
            getInterfaceInformation = canbusInterface.getQBridgeJ1708Info();
            returnCode = getInterfaceInformation.getReturnCodeRxd();
            android.util.Log.d(TAG, "J1708 Info Returned, Return code=" + returnCode);
        }
        else getInterfaceInformation = canbusInterface.getQBridgeJ1708Info();

        J1708InterfaceInformation j1708GetInformation = getInterfaceInformation.getQbJ1708Information();

        android.util.Log.d(TAG, "Information Retuned: J1708 Received packet count: " + getInterfaceInformation.getQbJ1708Information(). getRxCount());
        android.util.Log.d(TAG, "Information Retuned: J1708 Wait for Busy Bus Count: " + getInterfaceInformation.getQbJ1708Information().getWaitForBusyBusCount());
        android.util.Log.d(TAG, "Information Retuned: J1708 Collision Count: " + getInterfaceInformation.getQbJ1708Information().getCollisionCount());
        android.util.Log.d(TAG, "Information Retuned: J1708 Dropped Rx Count: " + getInterfaceInformation.getQbJ1708Information().getDroppedRxCount() );
        android.util.Log.d(TAG, "Information Retuned: J1708 Dropped Tx Confirm Count: " + getInterfaceInformation.getQbJ1708Information().getDroppedTxConfirmCount());
        android.util.Log.d(TAG, "Information Retuned: J1708 Dropped packets from host: " + getInterfaceInformation.getQbJ1708Information().getDroppedPacketsFromHost());
        android.util.Log.d(TAG, "Information Retuned: J1708 MID Filter enabled: " + getInterfaceInformation.getQbJ1708Information().isJ1708FiltersEnabled()  );
        android.util.Log.d(TAG, "Information Retuned: J1708 Transmit Confirm: " + getInterfaceInformation.getQbJ1708Information().isTransmitConfirm() );
        android.util.Log.d(TAG, "Information Retuned: J1708 RxQueue Overflowed: " + getInterfaceInformation.getQbJ1708Information().getRxQueueOverflowed()  );
        android.util.Log.d(TAG, "Information Retuned: J1708 Bus transitions detected since last time this command was issued: " + getInterfaceInformation.getQbJ1708Information().getBusTransitionDetected());
        android.util.Log.d(TAG, "Information Retuned: J1708 Current bus state: " + getInterfaceInformation.getQbJ1708Information().getCurrentBusState());
        android.util.Log.d(TAG, "Information Retuned: J1708 Number of free Tx buffers: " + getInterfaceInformation.getQbJ1708Information().getFreeTxBuffers());
        android.util.Log.d(TAG, "Information Retuned: J1708 Number of free Rx buffers: " + getInterfaceInformation.getQbJ1708Information().getFreeRxBuffers() );
        android.util.Log.d(TAG, "Information Retuned: J1708 overflow reported: " + getInterfaceInformation.getQbJ1708Information().getOverflowReported() );


    }

    void removeInterface(InterfaceWrapper wrappedInterface) {
        try {
            wrappedInterface.canbusInterface.remove();
        } catch (Exception e) {
            Log.e(TAG, "Unable to remove CanbusInterface() " + e.toString());
        }
    } // removeInterface()


    SocketWrapper createSocket(InterfaceWrapper wrappedInterface) {

        CanbusSocket socket = null;

        // open a new socket.
        try {
            socket = wrappedInterface.canbusInterface.createSocket();
            if (socket == null) {
                Log.e(TAG, "Socket not created .. returned NULL");
                return null;
            }
            // set socket options here
        } catch (Exception e) {
            Log.e(TAG, "Exception creating Socket: "  + e.toString(), e);
            return null;
        }
        return new SocketWrapper(socket);
    } // createSocket()


    boolean openSocket(SocketWrapper wrappedSocket, boolean discardBuffer) {
        try {
            wrappedSocket.canbusSocket.open();
        } catch (Exception e) {
            Log.e(TAG, "Exception opening Socket: " +  e.toString(), e);
            return false;
        }

        // we have to discard when opening a socket at a new bitrate, but this causes a 3 second gap in frame reception

        if (discardBuffer) {
            try {
                wrappedSocket.canbusSocket.discardInBuffer();
            } catch (Exception e) {
                Log.e(TAG, "Exception discarding Socket buffer: " + e.toString(), e);
                return false;
            }
        }

        return true;
    } // openSocket


    void closeSocket(SocketWrapper wrappedSocket) {
        // close the socket
        try {
            if (wrappedSocket.canbusSocket != null)
                wrappedSocket.canbusSocket.close();
            wrappedSocket.canbusSocket = null;
            wrappedSocket = null;
        } catch (Exception e) {
            Log.e(TAG, "Exception closeSocket()" + e.toString(), e);
        }
    } // closeSocket();


    //////////////////////////////////////////////////////////////////
    // isJ1708Supported()
    //  does the hardware support J1708 ?
    //////////////////////////////////////////////////////////////////
    public static boolean isJ1708Supported() {

        Log.v(TAG, "Testing isJ1708Supported?");
        CanbusInterface canInterface = new CanbusInterface();
        return canInterface.isJ1708Supported();

    } // isJ1708Supported?


} // VehicleBusHW
