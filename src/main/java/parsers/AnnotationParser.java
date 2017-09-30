package parsers;

import containers.AbstractBeanFactory;
import containers.Bean;
import containers.BeanFactory;
import containers.Property;
import org.apache.xpath.operations.Bool;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import javax.naming.LinkLoopException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class AnnotationParser extends AbstractParser {
    private Set<Class<?>> classes;
    private LinkedList<Property> properties;

    public AnnotationParser(String pack) {
        // stackoverflow
        List<ClassLoader> classLoadersList = new LinkedList<>();
        classLoadersList.add(ClasspathHelper.contextClassLoader());
        classLoadersList.add(ClasspathHelper.staticClassLoader());

        // stackoverflow
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setScanners(new SubTypesScanner(false /* don't exclude Object.class */), new ResourcesScanner())
                .setUrls(ClasspathHelper.forClassLoader(classLoadersList.toArray(new ClassLoader[0])))
                .filterInputsBy(new FilterBuilder().include(FilterBuilder.prefix(pack))));

        classes = reflections.getSubTypesOf(Object.class);
        properties = new LinkedList<>();
    }

    public void getBeans(AbstractBeanFactory bf) throws BeanConfigurationException {
        Bean bean;
        String id = null, param = null;
        Method postCons = null, preDes = null;
        char injectionType = ' ';
        Boolean isSingleton = null;
        Boolean byName = null;
        Annotation[] annos;
        String params[];
        Constructor beanCons = null;
        HashMap<String,Method> setters = new HashMap<>();
        for (Class c : classes) {
            injectionType = ' ';
            properties.clear();
            annos = c.getAnnotations();
            if (annos.length != 0 && annos[0].annotationType().getSimpleName().equals("Component")) {
                params = annos[0].toString().split("=");
                if(params[1].length() > 1)
                {
                    id = params[1].substring(0,params[1].length()-1);
                }
                else {
                    id = c.getSimpleName();
                }

                if (annos.length >= 2 && annos[1].annotationType().getSimpleName().equals("Scope")) {
                    params = annos[1].toString().split("=");
                    param = params[1].substring(0, params[1].length() - 1);
                    switch (param) {
                        case "Prototype":
                            isSingleton = false;
                            break;
                        case "Singleton":
                            isSingleton = true;
                            break;
                        default:
                            throw new BeanConfigurationException("Unrecognized scope \""+param+"\" in bean LOL.");
                    }
                } else {
                    isSingleton = true;
                }
                Method[] methods = c.getMethods();
                //TODO: save dependencies of the methods, read autowired parameters
                for (Method method : methods) {
                    Annotation[] methodAnnos = method.getAnnotations();
                    for (Annotation an : methodAnnos) {
                        String type = an.annotationType().getSimpleName();
                        switch (type) {
                            case "Autowired":
                                if (method.getName().contains("set")) {
                                    injectionType = 's';
                                    params = an.toString().split("=");
                                    if (params[1].length() > 1) { //byName
                                        byName = true;
                                        Class[] types = method.getParameterTypes();
                                        Property property = new Property();
                                        property.setRef(params[1].substring(0,params[1].length()-1));
                                        property.setName(params[1].substring(0,params[1].length()-1));
                                        property.setType(types[0]);
                                        properties.add(property);
                                        setters.put(property.getName(),method);
                                    } else {  //byType
                                        byName = false;
                                        Class[] types = method.getParameterTypes();
                                        Property property = new Property();
                                        property.setRef(types[0].getSimpleName());
                                        property.setName(types[0].getSimpleName());
                                        property.setType(types[0]);
                                        properties.add(property);
                                        setters.put(property.getName(),method);
                                    }
                                }
                                    break;
                                    case "PostInicialization":
                                        postCons = method;
                                        break;
                                    case "PreDestruction":
                                        preDes = method;
                                        break;
                        }
                    }
                }
                if(injectionType == ' ') //injection by constructor, byType
                {
                    for(Constructor con : c.getConstructors())
                    {
                        Annotation[] consAn = con.getDeclaredAnnotations();
                        if(consAn.length != 0 && consAn[0].annotationType().getSimpleName().equals("Autowired"))
                        {
                            injectionType = 'c';
                            byName = false;
                            beanCons = con;
                            for(Class conType : con.getParameterTypes())
                            {
                                Property property = new Property();
                                property.setRef(conType.getSimpleName());
                                property.setName(conType.getSimpleName());
                                property.setType(conType);
                                properties.add(property);
                            }
                        }
                    }
                }
                bean = new Bean(id, injectionType, isSingleton, byName, c, postCons, preDes, properties);
                bf.addBean(id, bean);
                bean.setSetters(setters);
            }
        }
    }
}
