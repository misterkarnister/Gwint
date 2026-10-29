/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package gwint;

public enum Faction {
    KROLESTWO_POLNOCY,
    SCOIATEL,
    POTWORY,
    KROLENSTWO_NILFGARDU,
    NEUTRAL
    
}

public enum Type {
    NORMAL,
    HERO,
    SPECIAL,
}
public enum Row {
    TYL,
    SRODEK,
    PRZOD,
    OTHER,
}
public enum Abillities {
    ZWINNOSC,
    BOHATER,
    MEDYK,
    MORAL,
    GRUPA,
    SZPIEG,
    BRAT
}

public class Karta {
    int ID;
    String name;
    int power;
    Faction faction;
    Row row;
    Type type;
    Abillities abills;
    int count;
}
