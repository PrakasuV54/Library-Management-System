package repository;

import enums.*;
import model.*;
import service.AuthService;
import service.BookService;
import service.MembershipPlanService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class SyntheticData {

    private final UserRepository userRepository;
    private final MembershipPlanRepository membershipPlanRepository;
    private final BookService bookService;
    private final AuthorRepository authorRepository;
    private final GenreRepository genreRepository;
    private final MembershipRepository membershipRepository;
    private final TransactionRepository transactionRepository;
    private final FineRepository fineRepository;
    private final PaymentRepository paymentRepository;
    private final AuditLogRepository auditLogRepository;
    private final CategoryRepository categoryRepository;
    private final BookCopyRepository bookCopyRepository;

    public SyntheticData(UserRepository userRepository, MembershipPlanRepository membershipPlanRepository, BookService bookService) {
        this(userRepository, membershipPlanRepository, bookService,
                bookService != null ? bookService.getAuthorRepository() : null,
                bookService != null ? bookService.getGenreRepository() : null);
    }

    public SyntheticData(UserRepository userRepository, MembershipPlanRepository membershipPlanRepository, BookService bookService,
                         AuthorRepository authorRepository, GenreRepository genreRepository) {
        this(userRepository, membershipPlanRepository, bookService,
                authorRepository, genreRepository,
                null, null, null, null, null, null, null);
    }

    public SyntheticData(UserRepository userRepository, MembershipPlanRepository membershipPlanRepository, BookService bookService,
                         AuthorRepository authorRepository, GenreRepository genreRepository,
                         MembershipRepository membershipRepository, TransactionRepository transactionRepository,
                         FineRepository fineRepository, PaymentRepository paymentRepository,
                         AuditLogRepository auditLogRepository, CategoryRepository categoryRepository,
                         BookCopyRepository bookCopyRepository) {
        this.userRepository = userRepository;
        this.membershipPlanRepository = membershipPlanRepository;
        this.bookService = bookService;

        DataStore ds = extractDataStore(userRepository);

        this.authorRepository = authorRepository != null ? authorRepository
                : (bookService != null && bookService.getAuthorRepository() != null ? bookService.getAuthorRepository()
                : (ds != null ? new InMemoryAuthorRepository(ds) : null));

        this.genreRepository = genreRepository != null ? genreRepository
                : (bookService != null && bookService.getGenreRepository() != null ? bookService.getGenreRepository()
                : (ds != null ? new InMemoryGenreRepository(ds) : null));

        this.membershipRepository = membershipRepository != null ? membershipRepository
                : (ds != null ? new InMemoryMembershipRepository(ds) : null);

        this.transactionRepository = transactionRepository != null ? transactionRepository
                : (ds != null ? new InMemoryTransactionRepository(ds) : null);

        this.fineRepository = fineRepository != null ? fineRepository
                : (ds != null ? new InMemoryFineRepository(ds) : null);

        this.paymentRepository = paymentRepository != null ? paymentRepository
                : (ds != null ? new InMemoryPaymentRepository(ds) : null);

        this.auditLogRepository = auditLogRepository != null ? auditLogRepository
                : (ds != null ? new InMemoryAuditLogRepository(ds) : null);

        this.categoryRepository = categoryRepository != null ? categoryRepository
                : (ds != null ? new InMemoryCategoryRepository(ds) : null);

        this.bookCopyRepository = bookCopyRepository != null ? bookCopyRepository
                : (ds != null ? new InMemoryBookCopyRepository(ds) : null);
    }

    private static DataStore extractDataStore(UserRepository userRepo) {
        if (userRepo instanceof InMemoryUserRepository) {
            return ((InMemoryUserRepository) userRepo).getDataStore();
        }
        return null;
    }

    public void syntheticData() {
        // Idempotency: If synthetic users are already present, do not duplicate
        if (userRepository != null && userRepository.findByPhone("9870000001") != null) {
            printSummary();
            return;
        }

        // ==========================================
        // 1. Existing Seed Users (Admin & Librarian)
        // ==========================================
        User admin = userRepository.findByPhone("9999999999");
        if (admin == null) {
            admin = new User("System Admin", "admin@library.com", "9999999999",
                    AuthService.hashPassword("admin123"), UserRole.ADMIN, UserStatus.ACTIVE);
            userRepository.save(admin);
        }

        User librarian = userRepository.findByPhone("8888888888");
        if (librarian == null) {
            librarian = new User("Head Librarian", "librarian@library.com", "8888888888",
                    AuthService.hashPassword("lib123"), UserRole.LIBRARIAN, UserStatus.ACTIVE);
            userRepository.save(librarian);
        }

        // Additional staff
        User lib2 = new User("Sarah Librarian", "sarah.lib@library.com", "8888888881",
                AuthService.hashPassword("lib123"), UserRole.LIBRARIAN, UserStatus.ACTIVE);
        userRepository.save(lib2);

        User admin2 = new User("Michael Admin", "michael.admin@library.com", "9999999991",
                AuthService.hashPassword("admin123"), UserRole.ADMIN, UserStatus.ACTIVE);
        userRepository.save(admin2);

        // ==========================================
        // 2. Existing Seed Plans & Additional Plans (>= 50 plans)
        // ==========================================
        MembershipPlan planStandard = membershipPlanRepository.findByName("Standard Plan");
        if (planStandard == null) {
            planStandard = new MembershipPlan(DataStore.getMembershipPlanId(), "Standard Plan", 299.0);
            membershipPlanRepository.save(planStandard);
        }
        MembershipPlan planPremium = membershipPlanRepository.findByName("Premium Plan");
        if (planPremium == null) {
            planPremium = new MembershipPlan(DataStore.getMembershipPlanId(), "Premium Plan", 599.0);
            membershipPlanRepository.save(planPremium);
        }
        MembershipPlan planFamily = membershipPlanRepository.findByName(MembershipPlanService.DEFAULT_FAMILY_PLAN_NAME);
        if (planFamily == null) {
            planFamily = new MembershipPlan(DataStore.getMembershipPlanId(), MembershipPlanService.DEFAULT_FAMILY_PLAN_NAME, MembershipPlanService.DEFAULT_FAMILY_PLAN_PRICE);
            membershipPlanRepository.save(planFamily);
        }
        MembershipPlan planFamilyPrem = membershipPlanRepository.findByName(MembershipPlanService.DEFAULT_FAMILY_PREMIUM_PLAN_NAME);
        if (planFamilyPrem == null) {
            planFamilyPrem = new MembershipPlan(DataStore.getMembershipPlanId(), MembershipPlanService.DEFAULT_FAMILY_PREMIUM_PLAN_NAME, MembershipPlanService.DEFAULT_FAMILY_PREMIUM_PLAN_PRICE);
            membershipPlanRepository.save(planFamilyPrem);
        }

        String[] additionalPlanNames = {
                "Student Standard Plan", "Student Premium Plan", "Senior Citizen Plan", "Senior Premium Plan",
                "Academic Scholar Plan", "Digital Access Plan", "Research Fellow Plan", "Educator Plan",
                "Weekend Reader Plan", "Annual Standard Plan", "Quarterly Standard Plan", "Monthly Standard Plan",
                "Corporate Tier 1 Plan", "Corporate Tier 2 Plan", "Corporate Tier 3 Plan", "Corporate Tier 4 Plan",
                "Corporate Tier 5 Plan", "Corporate Tier 6 Plan", "Corporate Tier 7 Plan", "Corporate Tier 8 Plan",
                "Corporate Tier 9 Plan", "Corporate Tier 10 Plan", "Young Reader Plan", "Early Bird Plan",
                "Night Owl Plan", "Unlimited Literature Plan", "Science Scholar Plan", "Technology Enthusiast Plan",
                "Arts & Humanities Plan", "Business Professional Plan", "Global Reader Plan", "Community Member Plan",
                "Institutional Tier 1 Plan", "Institutional Tier 2 Plan", "Institutional Tier 3 Plan", "Institutional Tier 4 Plan",
                "Institutional Tier 5 Plan", "Family Weekend Plan", "Family Academic Plan", "Family Scholar Plan",
                "Youth Development Plan", "Lifelong Learning Plan", "Special Collections Plan", "Archival Research Plan",
                "International Fellow Plan", "Executive Reader Plan"
        };
        for (int i = 0; i < additionalPlanNames.length; i++) {
            String pName = additionalPlanNames[i];
            if (membershipPlanRepository.findByName(pName) == null) {
                membershipPlanRepository.save(new MembershipPlan(DataStore.getMembershipPlanId(), pName, 150.0 + (i * 20.0)));
            }
        }

        // ==========================================
        // 3. Canonical Authors (>= 50 authors)
        // ==========================================
        Author authorMartin = getOrSaveAuthor("Robert C. Martin");
        Author authorGamma = getOrSaveAuthor("Erich Gamma");
        Author authorHelm = getOrSaveAuthor("Richard Helm");
        Author authorJohnson = getOrSaveAuthor("Ralph Johnson");
        Author authorVlissides = getOrSaveAuthor("John Vlissides");
        Author authorOrwell = getOrSaveAuthor("George Orwell");
        Author authorFitzgerald = getOrSaveAuthor("F. Scott Fitzgerald");

        String[] authorNames = {
                "Martin Fowler", "Kent Beck", "Joshua Bloch", "Brian Goetz", "Donald Knuth",
                "Bjarne Stroustrup", "Linus Torvalds", "Andrew Hunt", "David Thomas", "Cay Horstmann",
                "Herbert Schildt", "Kathy Sierra", "Bert Bates", "Bruce Eckel", "Jon Skeet",
                "Venkat Subramaniam", "Douglas Adams", "Arthur Conan Doyle", "Agatha Christie", "J.K. Rowling",
                "J.R.R. Tolkien", "Isaac Asimov", "Philip K. Dick", "Arthur C. Clarke", "Ray Bradbury",
                "Aldous Huxley", "William Shakespeare", "Leo Tolstoy", "Fyodor Dostoevsky", "Mark Twain",
                "Ernest Hemingway", "Jane Austen", "Charles Dickens", "Victor Hugo", "Jules Verne",
                "H.G. Wells", "Gabriel Garcia Marquez", "Haruki Murakami", "Stephen King", "Neil Gaiman",
                "Brandon Sanderson", "Terry Pratchett", "Frank Herbert", "William Gibson", "Dan Brown"
        };
        List<Author> extraAuthors = new ArrayList<>();
        for (String aName : authorNames) {
            extraAuthors.add(getOrSaveAuthor(aName));
        }

        // ==========================================
        // 4. Canonical Genres (>= 50 genres)
        // ==========================================
        Genre genreSoftwareEngineering = getOrSaveGenre("Software Engineering");
        Genre genreProgramming = getOrSaveGenre("Programming");
        Genre genreArchitecture = getOrSaveGenre("Architecture");
        Genre genreClassic = getOrSaveGenre("Classic");
        Genre genreDystopian = getOrSaveGenre("Dystopian");
        Genre genreLiterature = getOrSaveGenre("Literature");

        String[] genreNames = {
                "Computer Science", "Algorithms", "Data Structures", "Web Development", "Cloud Computing",
                "Artificial Intelligence", "Machine Learning", "Cybersecurity", "Databases", "Operating Systems",
                "Mobile Development", "DevOps", "Microservices", "Object-Oriented Design", "Functional Programming",
                "Distributed Systems", "Networking", "Science Fiction", "Fantasy", "Mystery",
                "Thriller", "Historical Fiction", "Romance", "Biography", "Autobiography",
                "Memoir", "Philosophy", "Psychology", "History", "Science",
                "Mathematics", "Physics", "Economics", "Business", "Finance",
                "Self-Help", "Leadership", "Management", "Design", "Art",
                "Music", "Poetry", "Drama", "Horror", "Adventure", "Graphic Novel"
        };
        List<Genre> extraGenres = new ArrayList<>();
        for (String gName : genreNames) {
            extraGenres.add(getOrSaveGenre(gName));
        }

        // ==========================================
        // 5. Categories (>= 50 categories)
        // ==========================================
        Category catTech = getOrSaveCategory("Technology");
        Category catFiction = getOrSaveCategory("Fiction");

        String[] categoryNames = {
                "Non-Fiction", "Reference", "Academic", "Textbooks", "Science & Nature",
                "Computing", "Engineering", "Humanities", "Social Sciences", "Arts & Photography",
                "Biographies & Memoirs", "Business & Money", "Children's Books", "History & Politics", "Law",
                "Medicine & Health", "Religion & Spirituality", "Travel", "Cooking & Food", "Crafts & Hobbies",
                "Self-Development", "Young Adult", "Comics", "Anthologies", "Journals",
                "Research Papers", "Essays", "Periodicals", "Encyclopedias", "Dictionaries",
                "Handbooks", "Manuals", "Guides", "Technical Reports", "Standard Specifications",
                "Case Studies", "Dissertations", "Monographs", "Conference Proceedings", "Rare Books",
                "Special Collections", "Audiobooks", "E-Books", "Media", "Archives",
                "Government Publications", "International Studies", "Environmental Studies", "Sports & Recreation"
        };
        List<Category> extraCategories = new ArrayList<>();
        for (String cName : categoryNames) {
            extraCategories.add(getOrSaveCategory(cName));
        }

        // ==========================================
        // 6. Existing Seed Books (Books 1 to 5)
        // ==========================================
        if (bookService.getBookByIsbn("978-0132350884") == null) {
            Book book1 = new Book("978-0132350884", "Clean Code", "Prentice Hall", 2008, "Handbook of Agile Software Craftsmanship");
            book1.addAuthor(authorMartin);
            book1.addGenre(genreSoftwareEngineering);
            book1.addGenre(genreProgramming);
            book1.addCategory(catTech);
            bookService.addBook(book1, 3, librarian);
        }

        if (bookService.getBookByIsbn("978-0134494166") == null) {
            Book book2 = new Book("978-0134494166", "Clean Architecture", "Prentice Hall", 2017, "A Craftsman's Guide to Software Structure and Design");
            book2.addAuthor(authorMartin);
            book2.addGenre(genreSoftwareEngineering);
            book2.addGenre(genreArchitecture);
            book2.addCategory(catTech);
            bookService.addBook(book2, 2, librarian);
        }

        if (bookService.getBookByIsbn("978-0201633610") == null) {
            Book book3 = new Book("978-0201633610", "Design Patterns", "Addison-Wesley", 1994, "Elements of Reusable Object-Oriented Software");
            book3.addAuthor(authorGamma);
            book3.addAuthor(authorHelm);
            book3.addAuthor(authorJohnson);
            book3.addAuthor(authorVlissides);
            book3.addGenre(genreSoftwareEngineering);
            book3.addGenre(genreProgramming);
            book3.addCategory(catTech);
            bookService.addBook(book3, 2, librarian);
        }

        if (bookService.getBookByIsbn("978-0451524935") == null) {
            Book book4 = new Book("978-0451524935", "1984", "Secker & Warburg", 1950, "Classic Dystopian Novel");
            book4.addAuthor(authorOrwell);
            book4.addGenre(genreClassic);
            book4.addGenre(genreDystopian);
            book4.addCategory(catFiction);
            bookService.addBook(book4, 2, librarian);
        }

        if (bookService.getBookByIsbn("978-0743273565") == null) {
            Book book5 = new Book("978-0743273565", "The Great Gatsby", "Charles Scribner's Sons", 1925, "Classic 1920s Novel");
            book5.addAuthor(authorFitzgerald);
            book5.addGenre(genreClassic);
            book5.addGenre(genreLiterature);
            book5.addCategory(catFiction);
            bookService.addBook(book5, 2, librarian);
        }

        // ==========================================
        // 7. Additional Books (Books 6 to 51 -> total 51 books)
        // ==========================================
        String[][] additionalBooksData = {
                {"Refactoring", "978-0201485677", "Addison-Wesley Professional", "1999", "Improving the Design of Existing Code"},
                {"Effective Java", "978-0134685991", "Addison-Wesley Academic", "2018", "Best practices for the Java platform"},
                {"Java Concurrency in Practice", "978-0321349606", "Pearson Education", "2006", "Comprehensive guide to concurrent programming"},
                {"The Pragmatic Programmer", "978-0201616224", "Addison-Wesley Longman", "1999", "From Journeyman to Master"},
                {"The Mythical Man-Month", "978-0201835953", "Addison-Wesley Publishing", "1995", "Essays on Software Engineering"},
                {"Structure and Interpretation of Computer Programs", "978-0262510875", "MIT Press", "1996", "Fundamental computer science principles"},
                {"Introduction to Algorithms", "978-0262033848", "The MIT Press", "2009", "Comprehensive textbook on algorithms"},
                {"Patterns of Enterprise Application Architecture", "978-0321127426", "Addison-Wesley Signature", "2002", "Practices for enterprise software systems"},
                {"Domain-Driven Design", "978-0321125217", "Addison-Wesley Boston", "2003", "Tackling Complexity in the Heart of Software"},
                {"Working Effectively with Legacy Code", "978-0131177055", "Prentice Hall PTR", "2004", "Strategies for maintaining legacy systems"},
                {"Code Complete", "978-0735619678", "Microsoft Press", "2004", "A Practical Handbook of Software Construction"},
                {"Head First Java", "978-0596009205", "O'Reilly Media", "2005", "A Brain-Friendly Guide to Learning Java"},
                {"Thinking in Java", "978-0131872486", "Prentice Hall Professional", "2006", "In-depth guide to object-oriented Java"},
                {"The C Programming Language", "978-0131103627", "Prentice-Hall", "1988", "The definitive guide to ANSI C"},
                {"The Art of Computer Programming", "978-0201896831", "Addison-Wesley Reading", "1997", "Fundamental Algorithms Volume 1"},
                {"Continuous Delivery", "978-0321601919", "Addison-Wesley Addison", "2010", "Reliable Software Releases through Automation"},
                {"Site Reliability Engineering", "978-1491929124", "O'Reilly Media Inc.", "2016", "How Google Runs Production Systems"},
                {"Building Microservices", "978-1491950357", "O'Reilly Publications", "2015", "Designing Fine-Grained Systems"},
                {"Designing Data-Intensive Applications", "978-1449373320", "O'Reilly Media Radar", "2017", "The Big Ideas Behind Reliable Systems"},
                {"Brave New World", "978-0060850524", "Harper Perennial", "1932", "Dystopian vision of a technologically controlled society"},
                {"Fahrenheit 451", "978-1451673319", "Simon & Schuster", "1953", "Classic story of a book-burning future"},
                {"Animal Farm", "978-0451526342", "Signet Classics", "1945", "A satirical allegorical novella"},
                {"To Kill a Mockingbird", "978-0061120084", "HarperCollins", "1960", "Classic American novel on justice"},
                {"Crime and Punishment", "978-0140449136", "Penguin Classics", "1866", "Psychological drama of guilt and redemption"},
                {"The Hobbit", "978-0547928227", "Houghton Mifflin", "1937", "High-fantasy adventure novel"},
                {"The Fellowship of the Ring", "978-0547928210", "Houghton Mifflin Harcourt", "1954", "First volume of The Lord of the Rings"},
                {"Dune", "978-0441172719", "Chilton Books", "1965", "Epic science fiction saga on Arrakis"},
                {"Neuromancer", "978-0441569595", "Ace Books", "1984", "Seminal cyberpunk novel"},
                {"Foundation", "978-0553293357", "Gnome Press", "1951", "The fall and rebirth of a galactic empire"},
                {"Do Androids Dream of Electric Sheep?", "978-0345404473", "Doubleday", "1968", "Philosophical exploration of artificial consciousness"},
                {"2001: A Space Odyssey", "978-0451457998", "New American Library", "1968", "Epic science fiction journey across the solar system"},
                {"I, Robot", "978-0553382562", "Grosset & Dunlap", "1950", "Collection of linked robot stories"},
                {"The Catcher in the Rye", "978-0316769488", "Little, Brown and Company", "1951", "Classic story of teenage alienation"},
                {"Moby-Dick", "978-1503280786", "Harper & Brothers", "1851", "Epic seafaring hunt for the great white whale"},
                {"Pride and Prejudice", "978-0141439518", "T. Egerton", "1813", "Romantic comedy of manners"},
                {"War and Peace", "978-1400079988", "The Russian Messenger", "1869", "Panoramic novel of the Napoleonic invasion"},
                {"Hamlet", "978-0743477123", "Folger Shakespeare Library", "1603", "Shakespearean tragedy of revenge and madness"},
                {"A Study in Scarlet", "978-1514699478", "Ward Lock & Co", "1887", "First appearance of Sherlock Holmes and Dr. Watson"},
                {"Murder on the Orient Express", "978-0062693662", "Collins Crime Club", "1934", "Celebrated Hercule Poirot mystery"},
                {"The Shining", "978-0307743657", "Doubleday Publishing", "1977", "Psychological horror masterpiece"},
                {"American Gods", "978-0062572288", "William Morrow", "2001", "Mythology and folklore clash with modern society"},
                {"The Way of Kings", "978-0765326355", "Tor Books", "2010", "Epic fantasy in the world of Roshar"},
                {"Good Omens", "978-0060853983", "Gollancz Books", "1990", "The Nice and Accurate Prophecies of Agnes Nutter"},
                {"A Game of Thrones", "978-0553103540", "Bantam Spectra", "1996", "First novel in A Song of Ice and Fire"},
                {"Thinking, Fast and Slow", "978-0374533557", "Farrar, Straus and Giroux", "2011", "Exploration of the two systems that drive how we think"},
                {"Sapiens: A Brief History of Humankind", "978-0062316097", "Dvir Publishing House", "2014", "A narrative history of human evolution"},
                {"The Phoenix Project", "978-0988262591", "IT Revolution Publishing", "2013", "A Novel about IT and DevOps"},
                {"Release It!", "978-1680502398", "Pragmatic Bookshelf", "2018", "Design and Deploy Production-Ready Software"},
                {"Accelerate", "978-1942788331", "IT Revolution Press", "2018", "Building and Scaling High Performing Tech Organizations"},
                {"Building Event-Driven Microservices", "978-1492057895", "O'Reilly Media Architecture", "2020", "Leveraging Organizational Data at Scale"}
        };

        List<Book> createdAdditionalBooks = new ArrayList<>();
        for (int i = 0; i < additionalBooksData.length; i++) {
            String[] bData = additionalBooksData[i];
            String title = bData[0];
            String isbn = bData[1];
            String publisher = bData[2];
            int year = Integer.parseInt(bData[3]);
            String desc = bData[4];

            if (bookService.getBookByIsbn(isbn) == null) {
                Book b = new Book(isbn, title, publisher, year, desc);
                Author assignedAuthor = extraAuthors.get(i % extraAuthors.size());
                b.addAuthor(assignedAuthor);

                Genre assignedGenre1 = extraGenres.get((i * 2) % extraGenres.size());
                Genre assignedGenre2 = extraGenres.get((i * 2 + 1) % extraGenres.size());
                b.addGenre(assignedGenre1);
                b.addGenre(assignedGenre2);

                Category assignedCategory = extraCategories.get(i % extraCategories.size());
                b.addCategory(assignedCategory);

                bookService.addBook(b, 2, librarian);
                createdAdditionalBooks.add(b);
            }
        }

        // ==========================================
        // 8. Generate 50 Synthetic Members
        // ==========================================
        String[] memberNames = {
                "James Smith", "Emily Johnson", "Michael Brown", "Emma Davis", "Daniel Wilson",
                "Sophia Taylor", "David Martinez", "Olivia Anderson", "Lucas Thomas", "Ava White",
                "Ethan Harris", "Isabella Martin", "Mason Clark", "Mia Rodriguez", "Alexander Lewis",
                "Charlotte Lee", "Benjamin Walker", "Amelia Hall", "William Allen", "Harper Young",
                "Jameson King", "Evelyn Wright", "Logan Scott", "Abigail Green", "Jacob Adams",
                "Emily Baker", "Henry Gonzalez", "Elizabeth Nelson", "Sebastian Carter", "Sofia Mitchell",
                "Jack Perez", "Avery Roberts", "Owen Turner", "Ella Phillips", "Samuel Campbell",
                "Scarlett Parker", "Matthew Evans", "Grace Edwards", "Joseph Collins", "Chloe Stewart",
                "Levi Sanchez", "Victoria Morris", "David Rogers", "Riley Reed", "John Cook",
                "Aria Morgan", "Wyatt Bell", "Lily Murphy", "Carter Bailey", "Aubrey Rivera"
        };

        List<User> syntheticMembers = new ArrayList<>();
        for (int i = 0; i < memberNames.length; i++) {
            String phone = String.format("987000%04d", (i + 1));
            String email = String.format("member%02d@library.com", (i + 1));
            UserStatus status = (i >= 46) ? UserStatus.INACTIVE : UserStatus.ACTIVE;
            User member = new User(memberNames[i], email, phone,
                    AuthService.hashPassword("MemberPass123!"), UserRole.MEMBER, status);
            userRepository.save(member);
            syntheticMembers.add(member);
        }

        // ==========================================
        // 9. Generate 50 Memberships
        // ==========================================
        if (membershipRepository != null) {
            LocalDate today = LocalDate.now();
            List<MembershipPlan> allPlans = membershipPlanRepository != null ? membershipPlanRepository.findAll() : new ArrayList<>();
            int planCount = allPlans.isEmpty() ? 1 : allPlans.size();

            for (int i = 0; i < syntheticMembers.size(); i++) {
                User member = syntheticMembers.get(i);
                int planId = allPlans.isEmpty() ? 101 : allPlans.get(i % planCount).getPlanId();
                MembershipStatus memStatus;
                LocalDate start;
                LocalDate end;

                if (i < 35) {
                    memStatus = MembershipStatus.ACTIVE;
                    start = today.minusDays(15);
                    end = today.plusDays(15);
                } else if (i < 45) {
                    memStatus = MembershipStatus.EXPIRED;
                    start = today.minusDays(60);
                    end = today.minusDays(10);
                } else {
                    memStatus = MembershipStatus.SUSPENDED;
                    start = today.minusDays(20);
                    end = today.plusDays(10);
                }

                int paymentId = 2000 + i;
                Membership mem = new Membership(DataStore.getMembershipId(), member.getPhone(), planId, start, end, memStatus, paymentId);
                membershipRepository.save(mem);
            }
        }

        // ==========================================
        // 10. Generate 55 Book Transactions
        // ==========================================
        List<Book> allBooks = bookService.getAllBooks();
        List<BookCopy> allCopies = bookCopyRepository != null ? bookCopyRepository.findAll() : new ArrayList<>();
        List<Transaction> createdTransactions = new ArrayList<>();

        if (transactionRepository != null && allBooks.size() >= 25 && allCopies.size() >= 50) {
            LocalDateTime now = LocalDateTime.now();
            LocalDate today = LocalDate.now();

            // 1) 20 Historical RETURNED transactions
            for (int i = 0; i < 20; i++) {
                User user = syntheticMembers.get(i % syntheticMembers.size());
                Book book = allBooks.get((i + 5) % allBooks.size());
                BookCopy copy = allCopies.get(i);
                int txId = DataStore.getTransactionId();
                Transaction tx = new Transaction(txId, user.getPhone(), book.getBookId(), copy.getBookCopyId(),
                        TransactionStatus.RETURNED, now.minusDays(40 + i), now.minusDays(35 + i),
                        today.minusDays(21 + i), now.minusDays(15 + i));
                transactionRepository.save(tx);
                createdTransactions.add(tx);
            }

            // 2) 10 Current On-Time ISSUED transactions
            for (int i = 0; i < 10; i++) {
                User user = syntheticMembers.get(i);
                Book book = allBooks.get((i + 10) % allBooks.size());
                BookCopy copy = allCopies.get(20 + i);
                copy.reserve();
                copy.issue();
                if (bookCopyRepository != null) bookCopyRepository.save(copy);

                int txId = DataStore.getTransactionId();
                Transaction tx = new Transaction(txId, user.getPhone(), book.getBookId(), copy.getBookCopyId(),
                        TransactionStatus.ISSUED, now.minusDays(3), now.minusDays(3),
                        today.plusDays(11), null);
                transactionRepository.save(tx);
                createdTransactions.add(tx);
            }

            // 3) 5 Currently OVERDUE ISSUED transactions
            for (int i = 0; i < 5; i++) {
                User user = syntheticMembers.get(10 + i);
                Book book = allBooks.get((i + 15) % allBooks.size());
                BookCopy copy = allCopies.get(30 + i);
                copy.reserve();
                copy.issue();
                if (bookCopyRepository != null) bookCopyRepository.save(copy);

                int txId = DataStore.getTransactionId();
                Transaction tx = new Transaction(txId, user.getPhone(), book.getBookId(), copy.getBookCopyId(),
                        TransactionStatus.ISSUED, now.minusDays(30), now.minusDays(25),
                        today.minusDays(5 + i), null);
                transactionRepository.save(tx);
                createdTransactions.add(tx);
            }

            // 4) 5 RESERVED transactions
            for (int i = 0; i < 5; i++) {
                User user = syntheticMembers.get(15 + i);
                Book book = allBooks.get((i + 20) % allBooks.size());
                BookCopy copy = allCopies.get(35 + i);
                copy.reserve();
                if (bookCopyRepository != null) bookCopyRepository.save(copy);

                int txId = DataStore.getTransactionId();
                Transaction tx = new Transaction(txId, user.getPhone(), book.getBookId(), copy.getBookCopyId(),
                        TransactionStatus.RESERVED, now.minusDays(2), null, null, null);
                transactionRepository.save(tx);
                createdTransactions.add(tx);
            }

            // 5) 5 READY_FOR_PICKUP transactions
            for (int i = 0; i < 5; i++) {
                User user = syntheticMembers.get(20 + i);
                Book book = allBooks.get((i + 25) % allBooks.size());
                BookCopy copy = allCopies.get(40 + i);
                copy.reserve();
                if (bookCopyRepository != null) bookCopyRepository.save(copy);

                int txId = DataStore.getTransactionId();
                Transaction tx = new Transaction(txId, user.getPhone(), book.getBookId(), copy.getBookCopyId(),
                        TransactionStatus.READY_FOR_PICKUP, now.minusDays(1), null, null, null);
                transactionRepository.save(tx);
                createdTransactions.add(tx);
            }

            // 6) 5 CANCELLED transactions
            for (int i = 0; i < 5; i++) {
                User user = syntheticMembers.get(25 + i);
                Book book = allBooks.get((i + 30) % allBooks.size());
                int txId = DataStore.getTransactionId();
                Transaction tx = new Transaction(txId, user.getPhone(), book.getBookId(), 0,
                        TransactionStatus.CANCELLED, now.minusDays(10), null, null, null);
                transactionRepository.save(tx);
                createdTransactions.add(tx);
            }

            // 7) 3 LOST transactions
            for (int i = 0; i < 3; i++) {
                User user = syntheticMembers.get(30 + i);
                Book book = allBooks.get((i + 35) % allBooks.size());
                BookCopy copy = allCopies.get(45 + i);
                copy.markLost();
                if (bookCopyRepository != null) bookCopyRepository.save(copy);

                int txId = DataStore.getTransactionId();
                Transaction tx = new Transaction(txId, user.getPhone(), book.getBookId(), copy.getBookCopyId(),
                        TransactionStatus.LOST, now.minusDays(20), now.minusDays(15), today.minusDays(1), null);
                transactionRepository.save(tx);
                createdTransactions.add(tx);
            }

            // 8) 2 DAMAGED transactions
            for (int i = 0; i < 2; i++) {
                User user = syntheticMembers.get(33 + i);
                Book book = allBooks.get((i + 40) % allBooks.size());
                BookCopy copy = allCopies.get(48 + i);
                copy.markDamaged();
                if (bookCopyRepository != null) bookCopyRepository.save(copy);

                int txId = DataStore.getTransactionId();
                Transaction tx = new Transaction(txId, user.getPhone(), book.getBookId(), copy.getBookCopyId(),
                        TransactionStatus.DAMAGED, now.minusDays(15), now.minusDays(10), today.plusDays(4), now.minusDays(2));
                transactionRepository.save(tx);
                createdTransactions.add(tx);
            }
        }

        // ==========================================
        // 11. Generate 52 Fines
        // ==========================================
        List<Fine> createdFines = new ArrayList<>();
        if (fineRepository != null && !createdTransactions.isEmpty()) {
            // 20 PAID Overdue fines (associated with returned transactions 0..19)
            for (int i = 0; i < 20; i++) {
                Transaction tx = createdTransactions.get(i);
                Fine f = new Fine(DataStore.getFineId(), tx.getUserPhone(), tx.getTransactionId(), 30.0, FineType.OVERDUE);
                f.applyPayment(30.0);
                fineRepository.save(f);
                createdFines.add(f);
            }

            // 5 PENDING Overdue fines (associated with overdue issued transactions 30..34)
            for (int i = 0; i < 5; i++) {
                Transaction tx = createdTransactions.get(30 + i);
                Fine f = new Fine(DataStore.getFineId(), tx.getUserPhone(), tx.getTransactionId(), 50.0, FineType.OVERDUE);
                fineRepository.save(f);
                createdFines.add(f);
            }

            // 10 PARTIALLY_PAID fines
            for (int i = 0; i < 10; i++) {
                Transaction tx = createdTransactions.get(i);
                Fine f = new Fine(DataStore.getFineId(), tx.getUserPhone(), tx.getTransactionId(), 40.0, FineType.OVERDUE);
                f.applyPayment(20.0);
                fineRepository.save(f);
                createdFines.add(f);
            }

            // 12 PENDING fines
            for (int i = 0; i < 12; i++) {
                Transaction tx = createdTransactions.get(i);
                Fine f = new Fine(DataStore.getFineId(), tx.getUserPhone(), tx.getTransactionId(), 25.0, FineType.OVERDUE);
                fineRepository.save(f);
                createdFines.add(f);
            }

            // 5 WAIVED fines
            for (int i = 0; i < 5; i++) {
                Transaction tx = createdTransactions.get(i);
                Fine f = new Fine(DataStore.getFineId(), tx.getUserPhone(), tx.getTransactionId(), 35.0, FineType.OVERDUE);
                f.waive();
                fineRepository.save(f);
                createdFines.add(f);
            }
        }

        // ==========================================
        // 12. Generate 55 Payments
        // ==========================================
        if (paymentRepository != null) {
            PaymentMethod[] methods = {PaymentMethod.UPI, PaymentMethod.CARD, PaymentMethod.NET_BANKING};

            // 20 Fine Payments for the 20 PAID fines
            for (int i = 0; i < 20 && i < createdFines.size(); i++) {
                Fine f = createdFines.get(i);
                int pId = DataStore.getPaymentId();
                Payment p = new Payment(pId, f.getFineId(), f.getUserPhone(), f.getAmount(), methods[i % methods.length]);
                p.markSuccess("REF_FINE_" + pId);
                paymentRepository.save(p);
            }

            // 10 Fine Payments for the 10 PARTIALLY_PAID fines
            for (int i = 0; i < 10 && (25 + i) < createdFines.size(); i++) {
                Fine f = createdFines.get(25 + i);
                int pId = DataStore.getPaymentId();
                Payment p = new Payment(pId, f.getFineId(), f.getUserPhone(), f.getAmountPaid(), methods[i % methods.length]);
                p.markSuccess("REF_PARTIAL_" + pId);
                paymentRepository.save(p);
            }

            // 25 Membership Payments
            if (membershipRepository != null) {
                List<Membership> allMems = membershipRepository.findAll();
                for (int i = 0; i < 25 && i < allMems.size(); i++) {
                    Membership mem = allMems.get(i);
                    int pId = DataStore.getPaymentId();
                    Payment p = Payment.forMembership(pId, mem.getMembershipId(), mem.getUserPhone(), 299.0, methods[i % methods.length]);
                    p.markSuccess("REF_MEM_" + pId);
                    paymentRepository.save(p);
                }
            }
        }

        // ==========================================
        // 13. Generate 55 Audit Logs
        // ==========================================
        if (auditLogRepository != null) {
            String[] actions = {
                    "BOOK_ADDED", "MEMBERSHIP_ACTIVATED", "BOOK_REQUESTED", "BOOK_ISSUED",
                    "BOOK_RETURNED", "FINE_CREATED", "FINE_PAYMENT_SUCCEEDED", "FINE_WAIVED"
            };

            for (int i = 0; i < 55; i++) {
                String action = actions[i % actions.length];
                String userPhone = (i % 2 == 0) ? "8888888888" : syntheticMembers.get(i % syntheticMembers.size()).getPhone();
                String entityType = action.startsWith("BOOK") ? "BOOK" : (action.startsWith("MEMBERSHIP") ? "MEMBERSHIP" : "FINE");
                String entityId = String.valueOf(100 + i);
                String details = "Synthetic audit log entry for " + action + " on entity " + entityId;

                Audit audit = new Audit(DataStore.getAuditId(), userPhone, action, entityType, entityId, details);
                auditLogRepository.save(audit);
            }
        }

        printSummary();
    }

    private Author getOrSaveAuthor(String name) {
        if (authorRepository != null) {
            Author existing = authorRepository.findByName(name);
            if (existing != null) return existing;
            Author created = new Author(DataStore.getAuthorId(), name);
            authorRepository.save(created);
            return created;
        }
        return new Author(DataStore.getAuthorId(), name);
    }

    private Genre getOrSaveGenre(String name) {
        if (genreRepository != null) {
            Genre existing = genreRepository.findByName(name);
            if (existing != null) return existing;
            Genre created = new Genre(DataStore.getGenreId(), name);
            genreRepository.save(created);
            return created;
        }
        return new Genre(DataStore.getGenreId(), name);
    }

    private Category getOrSaveCategory(String name) {
        if (categoryRepository != null) {
            for (Category c : categoryRepository.findAll()) {
                if (c.getName().equalsIgnoreCase(name)) return c;
            }
            Category created = new Category(DataStore.getCategoryId(), name);
            categoryRepository.save(created);
            return created;
        }
        return new Category(DataStore.getCategoryId(), name);
    }

    private void printSummary() {
        int users = userRepository != null ? userRepository.findAll().size() : 0;
        int memberships = membershipRepository != null ? membershipRepository.findAll().size() : 0;
        int plans = membershipPlanRepository != null ? membershipPlanRepository.findAll().size() : 0;
        int books = bookService != null ? bookService.getAllBooks().size() : 0;
        int copies = bookCopyRepository != null ? bookCopyRepository.findAll().size() : 0;
        int authors = authorRepository != null ? authorRepository.findAll().size() : 0;
        int genres = genreRepository != null ? genreRepository.findAll().size() : 0;
        int categories = categoryRepository != null ? categoryRepository.findAll().size() : 0;
        int transactions = transactionRepository != null ? transactionRepository.findAll().size() : 0;
        int fines = fineRepository != null ? fineRepository.findAll().size() : 0;
        int payments = paymentRepository != null ? paymentRepository.findAll().size() : 0;
        int audits = auditLogRepository != null ? auditLogRepository.findAll().size() : 0;

        Set<String> publishers = new HashSet<>();
        if (bookService != null) {
            for (Book b : bookService.getAllBooks()) {
                if (b.getPublisher() != null && !b.getPublisher().isBlank()) {
                    publishers.add(b.getPublisher().trim());
                }
            }
        }

        System.out.println("=== Synthetic Data Summary ===");
        System.out.println("Users: " + users);
        System.out.println("Memberships: " + memberships);
        System.out.println("Membership Plans: " + plans);
        System.out.println("Books: " + books);
        System.out.println("Book Copies: " + copies);
        System.out.println("Authors: " + authors);
        System.out.println("Genres: " + genres);
        System.out.println("Categories: " + categories);
        System.out.println("Publishers: " + publishers.size());
        System.out.println("Transactions: " + transactions);
        System.out.println("Fines: " + fines);
        System.out.println("Payments: " + payments);
        System.out.println("Audit Logs: " + audits);
    }
}
