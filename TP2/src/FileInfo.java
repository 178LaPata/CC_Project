import java.io.Serializable;

public class FileInfo implements Serializable, Comparable<FileInfo> {

    private String nome;

    //private int num_blocos;

    //private


    public FileInfo(String nome){
        this.nome = nome;
    }

    public String getNome() {
        return nome;
    }

    @Override
    public String toString() {
        return nome;
    }

    @Override
    public int compareTo(FileInfo other) {
        return this.nome.compareTo(other.nome);
    }

}
