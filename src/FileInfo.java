import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileInfo implements Comparable<FileInfo> {

    private String nome;
    private int blocos_quantidade;
    List<Integer> blocos_disponiveis;

    public FileInfo(String nome, int blocos_quantidade) {
        this.nome = nome;
        this.blocos_quantidade = blocos_quantidade;
    }

    public FileInfo(String nome, int blocos_quantidade, List<Integer> blocos_disponiveis) {
        this.nome = nome;
        this.blocos_quantidade = blocos_quantidade;
        this.blocos_disponiveis = blocos_disponiveis;
    }

    public byte[] serialize() throws IOException {

        String buffer;

        if (blocos_disponiveis != null)
            buffer = this.nome + " " + this.blocos_quantidade + " " + this.blocos_disponiveis;
        else
            buffer = this.nome + " " + this.blocos_quantidade;

        /*
        byte[] nome = this.nome.getBytes();

        byte[] blocos_quantidade = new byte[]{
                (byte) (this.blocos_quantidade >>> 24),
                (byte) (this.blocos_quantidade >>> 16),
                (byte) (this.blocos_quantidade >>> 8),
                (byte) this.blocos_quantidade
        };


         */
        /*
        ByteBuffer buffer = ByteBuffer.allocate(this.blocos_disponiveis.size());
        for (int i : blocos_disponiveis) {
            buffer.putInt(i);
        }
        byte[] blocos_disponiveis = buffer.array();

         */

/*
        byte[] combined = new byte[nome.length + blocos_quantidade.length];

        ByteBuffer info = ByteBuffer.wrap(combined);

        info.put(nome);
        info.put(blocos_quantidade);

 */

        //info.put(blocos_disponiveis);

        return buffer.getBytes();
    }

    public static FileInfo deserialize(byte[] info){

        String fds = new String(info, StandardCharsets.UTF_8).substring(1);

        String[] splited = fds.split("\\s+");

        String nome = splited[0];
        int blocos_quantidade = Integer.parseInt(splited[1]);
        List<Integer> blocos_disponiveis = new ArrayList<>();


        if (splited.length == 2){
            for (int i = 0; i<blocos_quantidade; i++){
                blocos_disponiveis.add(i);
            }
        }
        else{
            for (int i = 2; i<splited.length; i++){
                blocos_disponiveis.add(Integer.parseInt(splited[i]));
            }
        }

        FileInfo fileInfo = new FileInfo(nome,blocos_quantidade,blocos_disponiveis);

        System.out.println(fileInfo);


        return fileInfo;
    }





    @Override
    public String toString() {
        return this.nome + " " + this.blocos_quantidade + " " + this.blocos_disponiveis;
    }


    public String getNome() {
        return nome;
    }

    public int getBlocos_quantidade() {
        return blocos_quantidade;
    }


    @Override
    public int compareTo(FileInfo other) {
        return this.nome.compareTo(other.nome);
    }


}
