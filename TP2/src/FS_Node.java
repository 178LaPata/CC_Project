import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.stream.Collectors;


public class FS_Node {


    public static void main(String[] args) {

        Socket socket = null;

        try {
            String server_ip = args[1];
            int server_port = Integer.parseInt(args[2]);

            socket = new Socket(server_ip, server_port);

            System.out.println("Conexão FS Track Protocol com servidor " + server_ip + " porta " + server_port);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());


            // Ler file names no folder escolhido para partilhar com tracker
            File folder = new File(args[0]);
            if (!folder.exists()) {
                if (!folder.mkdir()) {
                    System.out.println("Erro ao criar pasta");
                    return;
                }
            }

            byte[] register_message = TPManager.registerMessage(folder.listFiles());


            if (register_message != null) {
                out.write(register_message);
                out.flush();
            }


            List<BlockToSend> blocksToSend = Collections.synchronizedList(new ArrayList<>());
            ConcurrentHashMap<BlockToReceive, byte[]> blocksToReceive = new ConcurrentHashMap<>();


            DatagramSocket socketUDP = new DatagramSocket(9090);

            UDPListener udpListener = new UDPListener(socketUDP, folder, blocksToSend, blocksToReceive, out);
            new Thread(udpListener).start();


            BufferedReader inStdin = new BufferedReader(new InputStreamReader(System.in));
            boolean loop = true;
            while (loop) {

                String input = inStdin.readLine();

                String[] option = input.split("\\s+");

                switch (option[0]) {

                    case "GET": {

                        //Verificar se nodo já tem o ficheiro
                        if (folder.listFiles() != null) {
                            boolean exists = false;
                            for (File f : folder.listFiles()) {
                                if (f.getName().equals(option[1])) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (exists){
                                System.out.println("Ficheiro já existe!");
                                break;
                            }

                        }

                        System.out.println("A iniciar transferência!");

                        out.write(TPManager.getFileMessage(option[1]));
                        out.flush();

                        byte[] totalNodesBytes = new byte[2];
                        in.readFully(totalNodesBytes, 0, 2);
                        int totalNodes = Serializer.twoBytesToInt(totalNodesBytes);

                        if (totalNodes == 0) {
                            System.out.println("Ficheiro não existe!");
                            break;
                        }


                        int totalBlocks = in.readInt();

                        //Concurrent Set of IPS
                        ConcurrentSkipListSet<String> ipNodes = new ConcurrentSkipListSet<>();

                        ConcurrentHashMap<Integer, List<String>> blockNodes = new ConcurrentHashMap<>();


                        for (int blockID = 0; blockID < totalBlocks; blockID++) {
                            byte[] hash = new byte[20];
                            in.readFully(hash, 0, 20);
                            blocksToReceive.put(new BlockToReceive(option[1], blockID), hash);
                            blockNodes.put(blockID, new ArrayList<>());
                        }


                        for (int node = 0; node < totalNodes; node++) {

                            byte[] ipBytes = new byte[4];
                            in.readFully(ipBytes, 0, 4);
                            String ip = InetAddress.getByAddress(ipBytes).getHostAddress();

                            int blocksAvailableAmount = in.readInt();

                            ipNodes.add(ip);
                            if (blocksAvailableAmount == 0) {
                                for (int blockID = 0; blockID < totalBlocks; blockID++) {
                                    blockNodes.get(blockID).add(ip);
                                }
                            } else {
                                for (int bI = 0; bI < blocksAvailableAmount; bI++) {
                                    int bloco = in.readInt();
                                    blockNodes.get(bloco).add(ip);
                                }
                            }
                        }


                        //open file with name option[1] and create it if it doesnt exist
                        File file = new File(folder, option[1]);
                        if (!file.exists()) {
                            if (!file.createNewFile()) {
                                System.out.println("Erro ao criar ficheiro");
                                return;
                            }
                        }

                        //Concurrent List of BlockPriority
                        List<BlockPriority> sortedBlockPriorityList =
                                blockNodes
                                        .entrySet().stream()
                                        .sorted(Comparator.comparingInt(entry -> entry.getValue().size()))
                                        .map(Map.Entry::getKey)
                                        .map(BlockPriority::new)
                                        .collect(Collectors.toList());

                        CopyOnWriteArrayList<BlockPriority> blockPriorityList = new CopyOnWriteArrayList<>(sortedBlockPriorityList);


                        ConcurrentSkipListSet<String> ipsToTest = new ConcurrentSkipListSet<>();

                        AtomicInteger blocksTransferedAtomic = new AtomicInteger(0);

                        CheckNode checkNode = new CheckNode(ipNodes, ipsToTest, out, in);
                        new Thread(checkNode).start();

                        UDPRequestBlock udpRequestBlock = new UDPRequestBlock(socketUDP, option[1], blockPriorityList, ipNodes,
                                blockNodes, blocksToReceive, ipsToTest, blocksTransferedAtomic, totalBlocks);
                        Thread[] threads = new Thread[totalNodes];

                        for (int i = 0; i < totalNodes; i++) {
                            threads[i] = new Thread(udpRequestBlock);
                            threads[i].start();
                        }

                        for (int i = 0; i < totalNodes; i++) {
                            threads[i].join();
                        }

                        checkNode.stopThread();

                        System.out.println("Transferência concluída");



                        break;
                    }


                    case "FILES": {
                        out.writeByte(3);
                        out.flush();
                        byte[] numberFilesBytes = new byte[2];
                        in.readFully(numberFilesBytes, 0, 2);
                        int numberFiles = Serializer.twoBytesToInt(numberFilesBytes);
                        if (numberFiles == 0) {
                            System.out.println("Não existem ficheiros disponíveis para transferência");
                            break;
                        }
                        List<String> namesList = new ArrayList<>();
                        for (int i = 0; i < numberFiles; i++) {
                            int nameSize = in.readByte();
                            byte[] nameBytes = new byte[nameSize];
                            in.readFully(nameBytes, 0, nameSize);
                            String name = new String(nameBytes, StandardCharsets.UTF_8);
                            namesList.add(name);
                        }
                        System.out.println(namesList);
                        break;
                    }


                    case "EXIT": {
                        out.writeByte(0);
                        out.flush();
                        udpListener.stopThread();
                        loop = false;
                        break;
                    }


                }


            }


        } catch (ConnectException e) {
            System.err.println("Conexão ao servidor falhada!");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            System.out.println("Terminando Programa...");
            if (socket != null) {
                try {
                    socket.shutdownInput();
                    socket.shutdownOutput();
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Erro ao fechar socket");
                }
            }
        }

    }


    private static class UDPRequestBlock extends Thread {

        private DatagramSocket socket;
        private String fileName;
        private CopyOnWriteArrayList<BlockPriority> blockPriorityList;
        private ConcurrentSkipListSet<String> ipsNodes;
        private ConcurrentHashMap<Integer, List<String>> nodesForBlocks;
        private ConcurrentHashMap<BlockToReceive, byte[]> blocksToReceive;
        private ConcurrentSkipListSet<String> ipsToTest;
        private AtomicInteger blocksTransferedAtomic;
        private int totalBlocks;


        public UDPRequestBlock(DatagramSocket socket, String fileName, CopyOnWriteArrayList<BlockPriority> blockPriorityList,
                               ConcurrentSkipListSet<String> ipsNodes, ConcurrentHashMap<Integer, List<String>> nodesForBlocks,
                               ConcurrentHashMap<BlockToReceive, byte[]> blocksToReceive, ConcurrentSkipListSet<String> ipsToTest,
                               AtomicInteger blocksTransferedAtomic, int totalBlocks) {
            try {
                this.socket = socket;
                this.fileName = fileName;
                this.blockPriorityList = blockPriorityList;
                this.ipsNodes = ipsNodes;
                this.nodesForBlocks = nodesForBlocks;
                this.blocksToReceive = blocksToReceive;
                this.ipsToTest = ipsToTest;
                this.blocksTransferedAtomic = blocksTransferedAtomic;
                this.totalBlocks = totalBlocks;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        @Override
        public void run() {

            while (!blockPriorityList.isEmpty()) {

                for (BlockPriority blockPriority : blockPriorityList) {

                    if (blockPriority.available) {

                        blockPriority.available = false;

                        //Create datagram to request for block
                        byte[] name = fileName.getBytes();
                        byte size_name = (byte) name.length;
                        byte[] blockID = Serializer.intToFourBytes(blockPriority.id);
                        ByteBuffer msg_buffer = ByteBuffer.allocate(2 + name.length + 4);
                        msg_buffer.put((byte) 0);
                        msg_buffer.put(size_name);
                        msg_buffer.put(name);
                        msg_buffer.put(blockID);
                        byte[] msg = msg_buffer.array();


                        List<String> ips = nodesForBlocks.get(blockPriority.id);

                        for (String ip : ips) {

                            if (ips.contains(ip)) {

                                ipsNodes.remove(ip);

                                BlockToReceive blockToReceive = new BlockToReceive(fileName, blockPriority.id);


                                try {

                                    DatagramPacket requestPacket = new DatagramPacket(msg, msg.length, InetAddress.getByName(ip), 9090);

                                    int attempt;
                                    for (attempt = 0; attempt < 3; attempt++) {
                                        socket.send(requestPacket);
                                        Thread.sleep(10);
                                        if (!blocksToReceive.containsKey(blockToReceive)) {
                                            blockPriorityList.remove(blockPriority);
                                            blocksTransferedAtomic.incrementAndGet();
                                            System.out.println(blocksTransferedAtomic + "/" + totalBlocks);
                                            break;
                                        }
                                    }
                                    if (attempt == 3) {
                                        blockPriority.available = true;
                                        ipsToTest.add(ip);
                                        //ask transfer if this ip is still available
                                        //if not, remove from ipsNodes
                                        //if yes, try again
                                    } else ipsNodes.add(ip);

                                } catch (IOException | InterruptedException ex) {
                                    throw new RuntimeException(ex);
                                }

                                if (blocksToReceive.get(blockToReceive) == null)
                                    break;
                            }
                        }

                    }

                }


            }


        }

    }


    public static class CheckNode extends Thread {

        private final ConcurrentSkipListSet<String> ipsNodes;
        private final ConcurrentSkipListSet<String> ipsToTest;
        private final DataOutputStream out;
        private final DataInputStream in;

        private volatile boolean stop = false;

        public CheckNode(ConcurrentSkipListSet<String> ipsNodes, ConcurrentSkipListSet<String> ipsToTest, DataOutputStream out, DataInputStream in) {
            this.ipsNodes = ipsNodes;
            this.ipsToTest = ipsToTest;
            this.out = out;
            this.in = in;
        }

        @Override
        public void run() {

            while (stop) {

                for (String ip : ipsToTest) {

                    try {
                        out.write(TPManager.checkNodeMessage(ip));
                        out.flush();
                        byte response = in.readByte();
                        if (response == 0)
                            ipsNodes.add(ip);
                        ipsToTest.remove(ip);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }

            }

        }

        public void stopThread() {
            stop = true;
            this.interrupt();
        }

    }


    public static class UDPListener extends Thread {

        private DatagramSocket socket;
        private byte[] buffer;
        private File folder;
        private List<BlockToSend> blocksToSend;
        private ConcurrentHashMap<BlockToReceive, byte[]> blocksToReceive;
        private DataOutputStream out;

        public UDPListener(DatagramSocket socket, File folder, List<BlockToSend> blocksToSend, ConcurrentHashMap<BlockToReceive, byte[]> blocksToReceive, DataOutputStream out) {
            try {
                // Create a DatagramSocket to listen on the specified port
                this.socket = socket;
                // Set a buffer for receiving data
                this.buffer = new byte[1024];
                this.folder = folder;
                this.blocksToSend = blocksToSend;
                this.blocksToReceive = blocksToReceive;
                this.out = out;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void stopThread() {
            socket.close();
            this.interrupt();
        }

        @Override
        public void run() {

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            while (true) {
                try {

                    // Receive data from the socket
                    socket.receive(packet);

                    new Thread(new UDPDataHandler(socket, packet, folder, blocksToSend, blocksToReceive, out)).start();

                } catch (IOException e) {
                    // If the socket was closed, just terminate the thread
                    if (socket.isClosed()) {
                        return;
                    }
                    e.printStackTrace();
                }
            }
        }

    }

    //Create UPDRequestHandler class
    public static class UDPDataHandler implements Runnable {

        private final DatagramSocket socket;
        private final DatagramPacket packet;
        private final File folder;
        private final List<BlockToSend> blocksToSend;
        private final ConcurrentHashMap<BlockToReceive, byte[]> blocksToReceive;
        private final DataOutputStream out;


        public UDPDataHandler(DatagramSocket socket, DatagramPacket packet, File folder, List<BlockToSend> blocksToSend, ConcurrentHashMap<BlockToReceive, byte[]> blocksToReceive, DataOutputStream out) {
            this.socket = socket;
            this.packet = packet;
            this.folder = folder;
            this.blocksToSend = blocksToSend;
            this.blocksToReceive = blocksToReceive;
            this.out = out;
        }

        @Override
        public void run() {

            try {

                byte[] receivedData = packet.getData();

                InetAddress senderAddress = packet.getAddress();
                int senderPort = packet.getPort();

                int size_name = receivedData[1];
                String name = new String(receivedData, 2, size_name);
                byte[] blockIDBytes = new byte[4];
                System.arraycopy(receivedData, 2 + size_name, blockIDBytes, 0, 4);
                int blockID = Serializer.fourBytesToInt(blockIDBytes);

                if (folder.listFiles() == null)
                    return;

                File file = null;
                for (File f : folder.listFiles()) {
                    // Check if the current file is the right name
                    if (f.getName().equals(name)) {
                        file = f;
                        break;
                    }
                }

                if (file == null)
                    return;


                if (receivedData[0] == 0) {

                    try {

                        BlockToSend blockToSend = new BlockToSend(name, blockID, senderAddress.getHostAddress());

                        if (!blocksToSend.contains(blockToSend))
                            blocksToSend.add(blockToSend);

                        // Open the file using RandomAccessFile
                        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
                            // Set the file pointer to the desired offset
                            randomAccessFile.seek(blockID * 500);
                            // Read 500 bytes from the current offset
                            byte[] dataBuffer = new byte[500];
                            int bytesread = randomAccessFile.read(dataBuffer);

                            byte[] data = new byte[bytesread];
                            System.arraycopy(dataBuffer, 0, data, 0, bytesread);

                            ByteBuffer msg_buffer = ByteBuffer.allocate(6 + size_name + data.length);
                            msg_buffer.put((byte) 1);
                            msg_buffer.put((byte) size_name);
                            msg_buffer.put(name.getBytes());
                            msg_buffer.put(Serializer.intToFourBytes(blockID));
                            msg_buffer.put(data);
                            byte[] msg = msg_buffer.array();

                            DatagramPacket responsePacket = new DatagramPacket(msg, msg.length, senderAddress, senderPort);


                            for (int attempt = 0; attempt < 3; attempt++) {
                                socket.send(responsePacket);
                                Thread.sleep(10);
                                if (!blocksToSend.contains(blockToSend))
                                    break;
                            }

                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    } catch (RuntimeException e) {
                        throw new RuntimeException(e);
                    }


                } else if (receivedData[0] == 1) {

                    BlockToReceive blockToReceive = new BlockToReceive(name, blockID);

                    byte[] nameFileBytes = file.getName().getBytes();
                    byte nameFileBytesSize = (byte) nameFileBytes.length;
                    ByteBuffer byteBuffer = ByteBuffer.allocate(1 + 1 + nameFileBytesSize + 4);
                    byteBuffer.put((byte) 2);
                    byteBuffer.put(nameFileBytesSize);
                    byteBuffer.put(nameFileBytes);
                    byteBuffer.put(Serializer.intToFourBytes(blockID));

                    //Send datagram
                    DatagramPacket ackPacket = new DatagramPacket(byteBuffer.array(), byteBuffer.array().length, senderAddress, senderPort);

                    if (!blocksToReceive.containsKey(blockToReceive)) {
                        socket.send(ackPacket);
                        return;
                    }

                    int receivedLength = packet.getLength(); // Actual length of received data

                    // Ignore empty or unused part of the packet
                    byte[] actualData = new byte[receivedLength - 6 - size_name];

                    // Copy the data from the packet to the actualData array
                    System.arraycopy(packet.getData(), 6 + size_name, actualData, 0, receivedLength - 6 - size_name);


                    MessageDigest digest = MessageDigest.getInstance("SHA-1");
                    digest.update(actualData, 0, actualData.length);


                    byte[] dataHashBytes = digest.digest();

                    if (Arrays.equals(blocksToReceive.get(blockToReceive), dataHashBytes)) {
                        blocksToReceive.remove(blockToReceive);
                        //Write data in file using RandomAccessFile
                        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
                            // Set the file pointer to the desired offset
                            randomAccessFile.seek(blockID * 500);
                            // Write 500 bytes from the current offset
                            randomAccessFile.write(actualData);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        byte[] updateMessage = TPManager.updateMessage(file.getName(), blockID);

                        //Send tcp message to tracker to update file info
                        out.write(updateMessage);
                        out.flush();

                        socket.send(ackPacket);
                    }


                } else if (receivedData[0] == 2) {

                    BlockToSend blockToSend = new BlockToSend(name, blockID, senderAddress.getHostAddress());

                    blocksToSend.remove(blockToSend);
                }


            } catch (Exception e) {
                if (socket.isClosed())
                    return;
                e.printStackTrace();
            }
        }
    }


    public static class BlockToSend {

        private final String nameFile;
        private final int id;
        private final String ip;

        public BlockToSend(String nameFile, int id, String ip) {
            this.nameFile = nameFile;
            this.id = id;
            this.ip = ip;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            BlockToSend other = (BlockToSend) obj;
            return id == other.id &&
                    Objects.equals(nameFile, other.nameFile) &&
                    Objects.equals(ip, other.ip);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nameFile, id, ip);
        }

    }


    public static class BlockToReceive {

        private final String nameFile;
        private final int id;

        public BlockToReceive(String nameFile, int id) {
            this.nameFile = nameFile;
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            BlockToReceive other = (BlockToReceive) obj;
            return id == other.id && Objects.equals(nameFile, other.nameFile);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

    }


    public static class BlockPriority {
        private int id;
        private boolean available;

        private BlockPriority(int id) {
            this.id = id;
            this.available = true;
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            BlockPriority other = (BlockPriority) obj;
            return id == other.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }


}
