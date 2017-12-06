package SerializableTest;

import SerializableTest.Utils.SerializableUtils;
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
    public void test(){
        User user = (User) SerializableUtils.deSerialByte(SerializableUtils.serialByte(new User("user", 15)));

        Person person = (Person)SerializableUtils.deSerialByte(SerializableUtils.serialByte(new Person("person", 18)));

        //System.out.println("user:"+user.toString());

        System.out.println("person:"+person.toString());
    }
}
