public class Hund extends Tier {

    int age = 2;
    // Die abstrakte Methode muss hier implementiert werden
    @Override
    public void lautGeben() {
        System.out.println("Der Hund bellt.");
    }

    public void alterAusgeben(){
        System.out.println(age);
    }

    public void changeAge(int age){
        this.age = age;
    }

    public int getAge(){
        age = 2;
        return this.age;
    }

    public void machKatze(){
        Katze cat = new Katze();
        cat.bewegen();
    }
}
