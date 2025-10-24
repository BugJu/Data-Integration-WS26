public class Main {

    public static void main(String[] args) {
        Hund hund = new Hund();
        Katze katze = new Katze();

        hund.lautGeben();
        katze.lautGeben();

        hund.bewegen();
        katze.bewegen();
    }
}