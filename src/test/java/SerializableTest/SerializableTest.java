package SerializableTest;

import SerializableTest.Utils.KryoSerializationUtils;
import SerializableTest.Utils.SerializableUtils;
import SerializableTest.Utils.SerializationUtil;
import SerializableTest.entity.Person;
import SerializableTest.entity.User;
import org.junit.Test;

/**
 * Created by lf52 on 2017/12/6.
 */
public class SerializableTest {

    /**
     * 关于transient关键字：
     *   1.一旦变量被transient修饰，变量将不再是对象持久化的一部分，该变量内容在序列化后无法获得访问。
     *   2.transient关键字只能修饰成员变量，而不能修饰本地变量，方法和类。
     *   3.被transient关键字修饰的变量不再能被序列化，一个静态变量不管是否被transient修饰，均不能被序列化。
     *    （反序列化后static型变量的值为当前JVM中对应static变量的值）
     */

    @Test
    public void test1(){
        User user = (User) SerializableUtils.deSerialByte(SerializableUtils.serialByte(new User("user", 15)));

        Person person = (Person)SerializableUtils.deSerialByte(SerializableUtils.serialByte(new Person("person", 18)));

        //System.out.println("user:"+user.toString());

        System.out.println("person:" + person.toString());
    }

    /**
     * test protostuff
     */
    @Test
    public void test2(){
        User user = new User("user", 15);
        Person person = new Person("person", 18);
        System.out.println(SerializationUtil.deserialize(SerializationUtil.serialize(user), User.class));
        System.out.println(SerializationUtil.deserialize(SerializationUtil.serialize(person), Person.class));
    }

    @Test
    public void test3(){
        User user = new User("user", 15);
        System.out.println(KryoSerializationUtils.deserializationObject(KryoSerializationUtils.serializationObject(user), User.class));
    }

    /**
     * 测试序列化以后文件的大小
     * kyro 34b
     * protostuff 9b
     */
    @Test
    public void test4(){
        User user = new User("user", 15);
        user.setName("fujun");
        KryoSerializationUtils.serializationObject("D:\\serialize\\kryo.txt", user);
        SerializationUtil.serialize("D:\\serialize\\stuff.txt", user);
    }

    /**
     * test protostuff序列化反序列化速度
     *
     * 1834  1860  1905
     */
    @Test
    public void test5(){
        User user = new User("user", 15);
        user.setName("fujun");
        long start = System.currentTimeMillis();
        for(int i = 0;i < 10000;i ++){
            SerializationUtil.deserialize(SerializationUtil.serialize(user), User.class);
        }
        long end = System.currentTimeMillis();
        long time = end - start;
        System.out.println("cost time : " + time + "ms");

    }

    /**
     * test kyro序列化反序列化速度
     *
     * 623  676  631
     */
    @Test
    public void test6(){
        User user = new User("user", 15);
        user.setName("fujun");
        long start = System.currentTimeMillis();
        for(int i = 0;i < 10000;i ++){
           KryoSerializationUtils.deserializationObject(KryoSerializationUtils.serializationObject(user), User.class);
        }
        long end = System.currentTimeMillis();
        long time = end - start;
        System.out.println("cost time : " + time + "ms");

    }
}
