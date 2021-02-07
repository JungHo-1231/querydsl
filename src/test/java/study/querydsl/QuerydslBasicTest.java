package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

@SpringBootTest
@Transactional

public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before(){
        queryFactory = new JPAQueryFactory(em);
        //given
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");

        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);

        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    @DisplayName("startJPQL")
    void startJPQL(){

        // member1을 찾아라
        String sqlString =
                "select m from Member m where " +
                "m.username = :username";

        Member findMember= em.createQuery(sqlString, Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertEquals("member1", findMember.getUsername());

    }

    @Test
    void startQuerydsl(){
//        QMember m = new QMember("m");

        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1")) // 파라미터 바인딩 처리
                .fetchOne();

        assertEquals("member1", findMember.getUsername());
    }

    @Test
    void search(){
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.eq(10)))
                .fetchOne();

        assertEquals("member1",findMember.getUsername());
    }

    @Test
    void search1(){
        Member findMember = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1")
                        .and(member.age.eq(10))
                )
                .fetchOne();

        assertEquals("member1",findMember.getUsername());
    }


    @Test
    void resultFetch(){
//        List<Member> fetch = queryFactory
//                .selectFrom(member)
//                .fetch();
//
//        Member fetchOne = queryFactory
//                .selectFrom(QMember.member)
//                .fetchOne();
//
//        Member fetchFirst = queryFactory
//                .selectFrom(QMember.member)
//                .fetchFirst();

//        QueryResults<Member> results = queryFactory
//                .selectFrom(member)
//                .fetchResults();
//
//        results.getTotal();
//        List<Member> content = results.getResults();

        long total = queryFactory
                .selectFrom(member)
                .fetchCount();
    }

    /**
     * 회원 정렬 순서
     * 1. 회원 나이 내렴차순(desc)
     * 2. 회원 이름 올림차순(asc)
     * 단 2에서 회원 이름이 없으면 마지막 출력 (nulls last)
     */
    @Test
    void sort(){
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);

        assertEquals("member5", member5.getUsername());
        assertEquals("member6", member6.getUsername());
        assertEquals(null, memberNull.getUsername());
    }

    @Test
    void paging1(){
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1) // 몇개를 생략할꺼야? 1을 적어주면 하나를 스킵한다는 의미
                .limit(2)
                .fetch();

        assertEquals(2, result.size());

    }


    @Test
    void paging2(){
        QueryResults<Member> queryResults = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1) // 몇개를 생략할꺼야? 1을 적어주면 하나를 스킵한다는 의미
                .limit(2)
                .fetchResults();

        assertEquals(4, queryResults.getTotal());
        assertEquals(2, queryResults.getLimit());
        assertEquals(1, queryResults.getOffset());
        assertEquals(2, queryResults.getResults().size());
    }

    @Test
    void aggregation(){
        List<Tuple> result = queryFactory
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();

        Tuple tuple = result.get(0);

        assertEquals(4,tuple.get(member.count()));
        assertEquals(100, tuple.get(member.age.sum()));
        assertEquals(25, tuple.get(member.age.avg()));
        assertEquals(40, tuple.get(member.age.max()));
        assertEquals(10, tuple.get(member.age.min()));

    }

    /**
     * 팀의 이름과 각 팀의 평균을 구해라.
     */
    @Test
    void group(){

        //given
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();
        //when
        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        //then
        assertEquals("teamA",teamA.get(team.name));
        assertEquals(15, teamA.get((member.age.avg())));

        assertEquals("teamB",teamB.get(team.name));
        assertEquals(35, teamB.get((member.age.avg())));

    }

    /**
     * 팀 A에 소속된 모든 회원
     */
    @Test
    void join(){
        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();
    }

    /**
     * 세타 조인
     * 회원의 이름이 팀 이름과 같은 회원 조회
     */
    @Test
    void theta_join(){
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Member> result = queryFactory
                .selectFrom(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();


    }


    /**
     *  예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     *  JPQL : select m ,t from Member m left join m.team t on t.name = 'teamA'
     */

    @Test
    void join_in_filtering(){

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }


    /**
     * 연관관계 없는 엔티티 외부 조인
     * 회원의 이름이 팀 이름과 같은 대상 외부 조인
     */
    @Test
    void join_on_no_relation(){
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name))
//                .join(team).on(member.username.eq(team.name))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    void fetchJoinNo(){
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        System.out.println("findMember = " + findMember);

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertEquals(false, loaded);
    }

    @Test
    void fetchJoinUse(){
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertEquals(true, loaded);
    }

    /**
     * 나이가 가장 많은 회원 조회
     */
    @Test
    void subQuery(){

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();

        assertEquals(40, result.get(0).getAge());
    }

    /**
     * 나이가 평균 이상인 회원
     */
    @Test
    void subQueryGoe(){

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();
    }

    /**
     * 서브 쿼리 in처리
     */
    @Test
    void subQueryIn(){

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions
                                .select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();
    }


    @Test
    void selectSubQuery(){
        QMember memberSub = new QMember("memberSub");
        List<Tuple> result = queryFactory
                .select(member.username,
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    void basicCase(){
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    void complexCase(){
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20")
                        .when(member.age.between(21, 30)).then("21~30")
                        .otherwise("기타"))
                .from(member)
                .fetch();


        for (String s : result) {
            System.out.println("s = " + s);
        }
    }


    /**
     * 상수
     */

    @Test
    void constant(){
        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();


        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 문자열 더하기
     */

    @Test
    void concat(){
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    void simpleProjection(){
        List<String> fetch = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for (String s : fetch) {
            System.out.println("s = " + s);
        }
    }

    @Test
    void tupleProjection(){
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username = " + username);
            System.out.println("age = " + age);
        }
    }
    @Test
    public void findDtoByJPQL(){
        List<MemberDto> result = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m", MemberDto.class)
                .getResultList();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findDtoBySetter(){
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }

    }

    @Test
    public void findDtoByField(){
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }

    }

    @Test
    public void findDtoByConstructor(){
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }

    }

    @Test
    public void findUserDto(){
        QMember memberSub = new QMember("memberSub");

        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                        member.username.as("name"),

                        ExpressionUtils.as(JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub), "age")
                ))
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }


    @Test
    void findDtoByQueryProjection(){
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    void dynamicQuery_BooleanBuilder(){
        String usernameParam = "member1";
        Integer ageParam =null;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertEquals(1, result.size());

    }

    List<Member> searchMember1(String usernameCond, Integer ageCond){

        BooleanBuilder builder = new BooleanBuilder();

        if (usernameCond != null){
            builder.and(member.username.eq(usernameCond));
        }

        if (ageCond != null){
            builder.and(member.age.eq(ageCond));
        }

        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    @Test
    void dynamicQuery_WhereParam(){
        String usernameParam = "member1";
        Integer ageParam =null;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertEquals(1, result.size());
    }

    List<Member> searchMember2(String usernameCond, Integer ageCond){
        return queryFactory
                .selectFrom(member)
                .where(allEq(usernameCond, ageCond))
//                .where(usernameEq(usernameCond), ageEq(ageCond))
                .fetch();
    }

    private BooleanExpression usernameEq(String usernameCond) {
        return usernameCond != null ? member.username.eq(usernameCond): null;
    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ?  member.age.eq(ageCond) : null;
    }

    private BooleanExpression allEq(String usernameCond, Integer ageCond){
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }


    @Test
    void bulkUpdate(){

        // member1 = 10 => 비회원
        // member2 = 20 => 비회원
        // member3 = 30 => 유지
        // member4 = 40 => 유지

        long count = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();

        em.flush();
        em.clear();


        List<Member> result = queryFactory
                .selectFrom(member)
                .fetch();

        for (Member member : result) {
            System.out.println("member = " + member);
        }
    }


    @Test
    void bulkAdd(){
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.add(1))
                .execute();
    }

    @Test
    void bulkMultiply(){
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.multiply(2))
                .execute();
    }

    @Test
    void bulkDelete(){
        queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();
    }


    @Test
    void sqlFunction(){
        List<String> result = queryFactory
                .select(Expressions.stringTemplate("function('replace', {0},{1},{2})",
                        member.username, "member", "M"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    void sqlFunction2(){
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
//                .where(member.username.eq(
//                        Expressions.stringTemplate("function('lower',{0})", member.username)))
                .where(member.username.eq(member.username.lower()))
                .fetch();


        for (String s : result) {
            System.out.println("s = " + s);
        }

    }

}



















































