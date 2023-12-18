import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FS_Tracker {

    public static void main(String[] args) {


        ServerSocket tracker = null;

        try {

            tracker = new ServerSocket(9090);

            System.out.println("Servidor ativo na porta: " + tracker.getLocalPort());


            ConcurrentHashMap<String, Set<FileInfo>> nodeFiles = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, Set<String>> fileLocations = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, List<byte[]>> fileHashes = new ConcurrentHashMap<>();


            while (true) {

                Socket node = tracker.accept();

                InetSocketAddress remoteSocketAddress = (InetSocketAddress) node.getRemoteSocketAddress();
                String ipNode = remoteSocketAddress.getAddress().getHostAddress();

                System.out.println("Novo nodo conectado: " + ipNode);

                NodeHandler nodeHandler = new NodeHandler(node, nodeFiles, fileLocations, fileHashes, ipNode);

                new Thread(nodeHandler).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (tracker != null) {
                try {
                    tracker.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    // ClientHandler class
    private static class NodeHandler implements Runnable {

        private final Socket nodeSocket;
        private final ConcurrentHashMap<String, Set<FileInfo>> nodeFiles;
        private final ConcurrentHashMap<String, Set<String>> fileLocations;
        private final ConcurrentHashMap<String, List<byte[]>> fileHashes;
        private final String ipNode;

        public NodeHandler(Socket socket, ConcurrentHashMap<String, Set<FileInfo>> nodeFiles, ConcurrentHashMap<String, Set<String>> fileLocations, ConcurrentHashMap<String, List<byte[]>> file_hash, String ipNode) {
            this.nodeSocket = socket;
            this.nodeFiles = nodeFiles;
            this.fileLocations = fileLocations;
            this.fileHashes = file_hash;
            this.ipNode = ipNode;
        }


        public void run() {

            try {

                DataOutputStream out = new DataOutputStream(nodeSocket.getOutputStream());
                DataInputStream in = new DataInputStream(nodeSocket.getInputStream());

                boolean loop = true;

                while (loop) {

                    byte choice = in.readByte();

                    switch (choice) {

                        //REGISTER
                        case 1: {

                            byte filesAmount = in.readByte();


                            for (byte fileI = 0; fileI < filesAmount; fileI++) {

                                byte sizeName = in.readByte();
                                byte[] nameBytes = new byte[sizeName];
                                in.readFully(nameBytes, 0, sizeName);
                                String fileName = new String(nameBytes, StandardCharsets.UTF_8);

                                int blockAmount = in.readInt();

                                int blocksAvailableAmount = in.readInt();

                                FileInfo fileInfo;
                                byte[] sha1Encoding = null;

                                if (blocksAvailableAmount == 0) {
                                    fileInfo = new FileInfo(fileName, blockAmount);
                                    sha1Encoding = new byte[20 * blockAmount];
                                    in.readFully(sha1Encoding, 0, 20 * blockAmount);
                                }
                                else {
                                    Set<Integer> blocksAvailable = new HashSet<>();
                                    for (int blockI = 0; blockI < blocksAvailableAmount; blockI++) {
                                        blocksAvailable.add(in.readInt());
                                    }
                                    fileInfo = new FileInfo(fileName, blockAmount, blocksAvailable);
                                }

                                if (!nodeFiles.containsKey(ipNode)) {
                                    Set<FileInfo> fileInfoSet = new HashSet<>();
                                    fileInfoSet.add(fileInfo);
                                    nodeFiles.put(ipNode, fileInfoSet);
                                } else {
                                    nodeFiles.get(ipNode).add(fileInfo);
                                }

                                if (!fileLocations.containsKey(fileName)) {
                                    Set<String> locations = new HashSet<>();
                                    locations.add(ipNode);
                                    fileLocations.put(fileName, locations);
                                } else {
                                    fileLocations.get(fileName).add(ipNode);
                                }

                                if (sha1Encoding != null) {
                                    if (!fileHashes.containsKey(fileName)) {
                                        List<byte[]> tempHashesList = new ArrayList<>();
                                        byte[] tempHash = new byte[20];
                                        for (int blockID = 0; blockID < blockAmount; blockID++) {
                                            System.arraycopy(sha1Encoding, blockID * 20, tempHash, 0, 20);
                                            tempHashesList.add(tempHash);
                                        }
                                        fileHashes.put(fileName, tempHashesList);
                                    }
                                }

                            }

                            break;
                        }


                        //UPDATE
                        case 2: {

                            byte sizeName = in.readByte();
                            byte[] nameBytes = new byte[sizeName];
                            in.readFully(nameBytes, 0, sizeName);
                            String fileName = new String(nameBytes, StandardCharsets.UTF_8);

                            int bloco = in.readInt();

                            Set<String> locations = fileLocations.get(fileName);

                            if (!locations.contains(ipNode)) {
                                locations.add(ipNode);

                                Set<Integer> blocksAvailable = new HashSet<>();
                                blocksAvailable.add(bloco);
                                int blockAmount = fileHashes.get(fileName).size();
                                FileInfo fileInfo = new FileInfo(fileName, blockAmount, blocksAvailable);

                                nodeFiles.computeIfAbsent(ipNode, fi -> new HashSet<>());

                                nodeFiles.get(ipNode).add(fileInfo);
                            }

                            else {
                                Set<FileInfo> fileInfoSet = nodeFiles.get(ipNode);
                                for (FileInfo fileInfo : fileInfoSet) {
                                    if (fileInfo.getNome().equals(fileName)) {
                                        fileInfo.addBlocoDisponivel(bloco);
                                        break;
                                    }
                                }
                            }


                            break;
                        }


                        //LIST FILES AVAILABLE
                        case 3: {
                            byte[] message = TPManager.filesAvailableMessage(fileHashes.keySet());
                            out.write(message);
                            out.flush();

                            break;
                        }


                        //GET FILE
                        case 4: {
                            byte sizeName = in.readByte();
                            byte[] nameBytes = new byte[sizeName];
                            in.readFully(nameBytes, 0, sizeName);
                            String fileName = new String(nameBytes, StandardCharsets.UTF_8);

                            Set<String> ips = fileLocations.get(fileName);

                            if (ips == null) {
                                out.writeByte(0);
                                out.flush();
                                break;
                            }

                            int sizeInfos = 0;
                            List<byte[]> nodeInfos = new ArrayList<>();

                            for (String ip : ips) {

                                Set<FileInfo> fileInfoSet = nodeFiles.get(ip);
                                FileInfo fileInfo = null;
                                for (FileInfo f : fileInfoSet)
                                    if (f.getNome().equals(fileName)) {
                                        fileInfo = f;
                                        break;
                                    }

                                if (fileInfo.isComplete()) {
                                    ByteBuffer fileInfoByteBuffer = ByteBuffer.allocate(4 + 4);
                                    byte[] ipBytes = InetAddress.getByName(ip).getAddress();
                                    fileInfoByteBuffer.put(ipBytes);
                                    byte[] blockAmmount = new byte[4];
                                    fileInfoByteBuffer.put(blockAmmount);
                                    byte[] info = fileInfoByteBuffer.array();
                                    nodeInfos.add(info);
                                    sizeInfos += info.length;
                                }
                                else {
                                    Set<Integer> blocksAvailable = fileInfo.getBlocksAvailable();
                                    ByteBuffer fileInfoByteBuffer = ByteBuffer.allocate(4 + 4 + 4 * blocksAvailable.size());
                                    byte[] ipBytes = InetAddress.getByName(ip).getAddress();
                                    fileInfoByteBuffer.put(ipBytes);
                                    fileInfoByteBuffer.put(Serializer.intToFourBytes(blocksAvailable.size()));
                                    for (int blockID : blocksAvailable) {
                                        fileInfoByteBuffer.put(Serializer.intToFourBytes(blockID));
                                    }
                                    byte[] info = fileInfoByteBuffer.array();
                                    sizeInfos += info.length;
                                    nodeInfos.add(info);
                                }

                            }

                            List<byte[]> hashes = fileHashes.get(fileName);

                            ByteBuffer byteBuffer = ByteBuffer.allocate(2 + 4 + hashes.size() * 20 + sizeInfos);

                            byteBuffer.put(Serializer.intToTwoBytes(ips.size()));
                            byteBuffer.put(Serializer.intToFourBytes(fileHashes.get(fileName).size()));
                            for (byte[] hash : hashes) {
                                byteBuffer.put(hash);
                            }
                            for (byte[] nodeInfo : nodeInfos)
                                byteBuffer.put(nodeInfo);

                            out.write(byteBuffer.array());
                            out.flush();

                            break;
                        }


                        //CHECK IF NODE IS STILL CONNECTED
                        case 8: {
                            byte[] message = new byte[4];
                            in.readFully(message, 0, 4);
                            String ip = InetAddress.getByAddress(message).getHostAddress();
                            if (nodeFiles.containsKey(ip))
                                out.writeByte(1);
                            else
                                out.writeByte(0);

                            out.flush();
                            break;
                        }


                        //EXIT
                        case 0: {
                            System.out.println("Conexão fechada com nodo: " + nodeSocket.getInetAddress().getHostAddress());
                            //Check if node is in node_files
                            //If it is, remove it and remove it's files from file_locations
                            //Also, if file from file_locations has 0 elements, remove it
                            if (nodeFiles.containsKey(ipNode)) {
                                Set<FileInfo> fileInfoSet = nodeFiles.remove(ipNode);
                                for (FileInfo fileInfo : fileInfoSet) {
                                    Set<String> locations = fileLocations.get(fileInfo.getNome());
                                    locations.remove(ipNode);
                                    if (locations.isEmpty()) {
                                        fileLocations.remove(fileInfo.getNome());
                                    }
                                }
                            }

                            loop = false;
                            break;
                        }


                    }


                }



            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    nodeSocket.shutdownInput();
                    nodeSocket.shutdownOutput();
                    nodeSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


}
