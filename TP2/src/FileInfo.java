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


    @Override
    public String toString() {
        if (blocos_disponiveis == null)
            return this.nome + ";" + this.blocos_quantidade;
        else
            return this.nome + ";" + this.blocos_quantidade + ";" + this.blocos_disponiveis;
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




    public byte[] fileInfoToBytes(){

        byte size_name = (byte) this.nome.length();
        byte[] name = this.nome.getBytes();
        byte[] blocos_quantidade = Serializer.intToFourBytes(this.blocos_quantidade);
        byte[] blocos_disponiveis_size = Serializer.intToFourBytes(this.blocos_disponiveis.size());
        byte[] blocos_disponiveis;

        if (this.blocos_disponiveis.isEmpty())
            blocos_disponiveis = new byte[0];

        else {
            ByteBuffer buffer_blocos_disponiveis = ByteBuffer.allocate(this.blocos_disponiveis.size()*4);

            for (Integer i : this.blocos_disponiveis)
                buffer_blocos_disponiveis.put(Serializer.intToFourBytes(i));

            blocos_disponiveis = buffer_blocos_disponiveis.array();
        }

        ByteBuffer buffer = ByteBuffer.allocate(1+name.length+blocos_quantidade.length + blocos_disponiveis.length);

        buffer.put(size_name);
        buffer.put(name);
        buffer.put(blocos_quantidade);
        buffer.put(blocos_disponiveis_size);
        buffer.put(blocos_disponiveis);

        return buffer.array();
    }

    public FileInfo bytesToFileInfo(){

    }





}
