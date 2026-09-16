import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class AnnotationReflectionDemo {
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface RequiredText {
        String message() default "文本不能为空";
    }

    public static class NoticeForm {
        @RequiredText(message = "标题不能为空")
        private String title;

        public NoticeForm(String title) {
            this.title = title;
        }

        public String preview(String prefix) {
            return prefix + title;
        }
    }

    static List<String> validate(Object target) throws IllegalAccessException {
        List<String> errors = new ArrayList<>();
        for (Field field : target.getClass().getDeclaredFields()) {
            RequiredText rule = field.getAnnotation(RequiredText.class);
            if (rule == null) {
                continue;
            }
            // 本例的注解只支持 String 字段，错误用法直接报告。
            if (field.getType() != String.class) {
                throw new IllegalArgumentException("@RequiredText 只能标在 String 字段上");
            }
            if (!field.trySetAccessible()) {
                throw new IllegalAccessException("不能访问字段：" + field.getName());
            }
            String value = (String) field.get(target);
            if (value == null || value.isBlank()) {
                errors.add(rule.message());
            }
        }
        return errors;
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Class<NoticeForm> type = NoticeForm.class;
        NoticeForm form = type.getConstructor(String.class).newInstance("Java 学习");

        Method method = type.getMethod("preview", String.class);
        System.out.println(method.invoke(form, "预览："));
        System.out.println("正常标题：" + validate(form));

        Field titleField = type.getDeclaredField("title");
        if (!titleField.trySetAccessible()) {
            throw new IllegalAccessException("不能访问 title");
        }
        titleField.set(form, "   ");
        System.out.println("空白标题：" + validate(form));

        titleField.set(form, null);
        System.out.println("null 标题：" + validate(form));
    }
}
