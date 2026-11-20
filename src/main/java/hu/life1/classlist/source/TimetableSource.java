package hu.life1.classlist.source;

import hu.life1.classlist.model.Branch;
import hu.life1.classlist.model.ClassSession;

import java.util.List;

/** One órarend / timetable source family (Life1 booking, Prestige, Lite, …). */
public interface TimetableSource {

    /** Human-readable family name for logs. */
    String familyName();

    /** Branches this source collects. */
    List<Branch> branches();

    /**
     * Collect every future class currently published for the given branch.
     * Implementations navigate weeks / rooms / dayparts as needed.
     */
    List<ClassSession> collectFutureClasses(Branch branch) throws Exception;
}
