import java.io.*;
import java.net.ConnectException;
import java.net.Socket;

public class FS_Node {

    public static void main(String[] args) throws IOException {

        try{
            String server_ip = args[1];
            int server_port = Integer.parseInt(args[2]);

            Socket socket = new Socket(server_ip,server_port);

            System.out.println("Conexão FS Track Protocol com servidor " + server_ip + " porta " + server_port);

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());


            // Ler file names no folder escolhido para partilhar com tracker
            File folder = new File(args[0]);
            File[] listOfFiles = folder.listFiles();

            if (listOfFiles != null) {

                out.writeUTF("UPDATE");
                out.writeInt(listOfFiles.length);
                out.flush();

                for (int i = 0; i < listOfFiles.length; i++) {
                    if (listOfFiles[i].isFile()) {
                        out.writeObject(new FileInfo(listOfFiles[i].getName()));
                        out.flush();
                    }
                /*
                else if (listOfFiles[i].isDirectory()) {
                    System.out.println("Directory " + listOfFiles[i].getName());
                }
                 */
                }


            }

            BufferedReader inStdin = new BufferedReader(new InputStreamReader(System.in));

            boolean loop = true;

            while(loop){

                String input = inStdin.readLine();

                String[] option = input.split("\\s+");

                switch (option[0]){

                    case "GET":{

                        out.writeUTF("GET");
                        out.writeUTF(option[1]);
                        out.flush();

                    }

                    case "QUIT":{
                        out.writeUTF("QUIT");
                        out.flush();
                    }

                }


            }



            System.out.println("Terminando Programa...");

            socket.shutdownInput();
            socket.shutdownOutput();
            socket.close();
        }

        catch (ConnectException e){
            System.err.println("Conexão ao servidor falhada!");
        }
        catch (IOException e){
            e.printStackTrace();
        }



    }

}