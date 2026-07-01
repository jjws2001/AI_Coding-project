package com.aicoding;

import com.aicoding.Entity.model.CustomOAuth2User;
import com.aicoding.Entity.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AiCodingPlatformApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void ReflectTest() throws IllegalAccessException {
        User user = new User();
        user.setUsername("tester");
        CustomOAuth2User customOAuth2User = new CustomOAuth2User(user, Map.of("login", "tester"));
        Map<String, Object> map = new HashMap<>();
        Class<?> clazz = CustomOAuth2User.class;
        for (Field field:clazz.getDeclaredFields()) {
            field.setAccessible(true);
            map.put(field.getName(), field.get(customOAuth2User));
        }
        assertThat(map).containsKeys("user", "attributes");
    }

    @Test
    void ComparatorTest() {
        int[] nums = {3, 30, 34, 5, 9};
        Integer[] integers = new Integer[nums.length];
        for (int i = 0; i < nums.length; i++) {
            integers[i] = nums[i];
        }
        Comparator<Integer> ctr = (a, b) -> {
            String s1 = String.valueOf(a);
            String s2 = String.valueOf(b);
            int i = 0, j = 0;
            while (i < s1.length() && j < s2.length()) {
                if (s1.charAt(i) != s2.charAt(j)) {
                    return s1.charAt(i) - s2.charAt(j);
                } else  {
                    if (s1.length() > s2.length() && i == s1.length() - 1) {
                        return s1.charAt(i) - s2.charAt(j);
                    } else if  (s2.length() > s1.length() && j == s2.length() - 1) {
                        return s2.charAt(i) - s1.charAt(j);
                    }
                    i++;
                    j++;
                    if (i == s1.length() && j < s2.length()) {
                        i = 0;
                    } else if (j == s2.length() && i < s1.length()) {
                        j = 0;
                    }
                }
            }
            return 0;
        };
        Arrays.sort(integers, ctr);
        Iterator<Integer> it = Arrays.asList(integers).iterator();
        String res = "";
        for (int i = 0;i < integers.length;i++) {
            res = res + integers[i];
        }
        assertThat(res).isNotBlank();
    }

}
