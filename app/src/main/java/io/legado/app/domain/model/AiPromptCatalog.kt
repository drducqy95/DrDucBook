package io.legado.app.domain.model

import androidx.annotation.Keep

@Keep
data class AiPromptCatalogTemplate(
    val id: String,
    val taskType: String,
    val name: String,
    val description: String,
    val prompt: String,
)

/**
 * Versioned starter prompts inspired by the context-oriented workflow in DrDuc AI Trans.
 *
 * Catalog entries are immutable samples. Importing one creates a normal user preset, so later
 * catalog updates never overwrite a prompt that the user has edited.
 */
object AiPromptCatalog {

    val supportedTaskTypes = listOf(
        AiTaskType.TRANSLATE_CHAPTER,
        AiTaskType.CHAT,
        AiTaskType.SUMMARIZE_CHAPTER,
        AiTaskType.SUMMARIZE_BOOK,
        AiTaskType.EXPLAIN_SELECTION,
        AiTaskType.CLEAN_SELECTION,
        AiTaskType.TEXT_FACTORY,
        AiTaskType.REWRITE_TEXT,
        AiTaskType.AUTHORING_DIRECTOR,
        AiTaskType.AUTHORING_WRITER,
        AiTaskType.GENERATE_STORY_IMAGE,
    )

    val templates: List<AiPromptCatalogTemplate> = listOf(
        AiPromptCatalogTemplate(
            id = "translation_context_auto_v3",
            taskType = AiTaskType.TRANSLATE_CHAPTER,
            name = "Tự nhận diện bối cảnh · Khuyến nghị",
            description = "Đọc chunk lân cận và từ điển để tự chọn xưng hô theo từng thế giới.",
            prompt = TranslationConstants.DEFAULT_PROMPT,
        ),
        translationStyle(
            id = "context_ancient_eastern_v3",
            name = "Cổ đại Đông phương / Tiên hiệp",
            description = "Cổ phong, kiếm hiệp, tiên hiệp; khóa vai vế và tránh xưng hô hiện đại sai cảnh.",
            style = """
                <vai_tro>Dịch giả văn học cổ đại Đông phương, kiếm hiệp, tiên hiệp và huyền huyễn.</vai_tro>
                <muc_tieu>Biên tập thành tiếng Việt cổ phong tự nhiên nhưng trung thành, không lạm dụng từ Hán-Việt tối nghĩa.</muc_tieu>
                <xung_ho>
                - Chọn theo quan hệ đã biết: trẫm–khanh/thần, vi sư–đồ nhi, tiền bối–vãn bối, tại hạ–các hạ, ta–ngươi.
                - Không dùng cậu–tớ, mình–bạn hoặc anh/anh ấy/cô/cô ấy trong lời kể cảnh cổ đại, trừ khi nguyên tác thể hiện lời nói hiện đại có chủ ý.
                - Ngôi ba nam trung tính dùng tên hoặc hắn; không đổi qua lại hắn/y/chàng/anh. Chưa rõ giới tính hoặc tuổi thì dùng tên/cách gọi trung tính.
                </xung_ho>
                <thuat_ngu>Giữ Name và glossary; phân biệt công pháp, cảnh giới, pháp bảo, tông môn. Tên đứng trước chức vị khi phù hợp: Lý trưởng lão.</thuat_ngu>
                <tinh_lien_tuc>Mọi Name, VietPhrase và Luật Nhân đã có là khóa liên chương; chỉ người dùng được sửa trong từ điển.</tinh_lien_tuc>
                <dinh_dang>Mỗi lượt thoại xuống dòng trong ngoặc kép; chỉ xuất bản dịch.</dinh_dang>
            """.trimIndent(),
        ),
        translationStyle(
            id = "context_modern_v3",
            name = "Hiện đại / Đô thị / Học đường",
            description = "Tiếng Việt đương đại, xưng hô theo tuổi và quan hệ; không cổ phong hóa.",
            style = """
                <vai_tro>Dịch giả tiểu thuyết hiện đại, đô thị, nghề nghiệp và học đường.</vai_tro>
                <muc_tieu>Văn phong tự nhiên, tiết chế, đúng sắc thái xã hội và mức độ thân mật của nhân vật.</muc_tieu>
                <xung_ho>
                - Dùng tôi–anh/chị/cô/chú/ông/bà theo tuổi/vai; cậu–tớ hoặc mình–bạn chỉ cho người ngang hàng, thân mật đã được xác lập.
                - Không tự đưa trẫm, bổn tọa, tại hạ, huynh–đệ, vi sư–đồ nhi vào bối cảnh hiện đại.
                - Ngôi ba dùng tên/anh/cô/ông/bà/họ; “hắn” chỉ khi giọng kể xa cách hoặc đối địch. Không tự đổi giới tính.
                </xung_ho>
                <thuat_ngu>Giữ đúng chức danh, pháp lý, y khoa, công nghệ; glossary và tên riêng được ưu tiên hơn suy đoán.</thuat_ngu>
                <tinh_lien_tuc>Mọi Name, VietPhrase và Luật Nhân đã có là khóa liên chương; chỉ người dùng được sửa trong từ điển.</tinh_lien_tuc>
                <dinh_dang>Mỗi lượt thoại xuống dòng trong ngoặc kép; không thêm lời dẫn hay chú thích.</dinh_dang>
            """.trimIndent(),
        ),
        translationStyle(
            id = "context_western_fantasy_v3",
            name = "Kỳ huyễn phương Tây",
            description = "Ma pháp, quý tộc, kỵ sĩ; không trộn hệ danh xưng Đông phương.",
            style = """
                <vai_tro>Dịch giả kỳ huyễn, trung cổ và thần thoại phương Tây.</vai_tro>
                <muc_tieu>Giữ không khí phương Tây, tên Latin và hệ tước vị; văn Việt tự nhiên, không Hán hóa máy móc.</muc_tieu>
                <xung_ho>
                - Dùng tôi/ta–ngài/ngươi theo mức trang trọng; dùng đúng Đức vua, Nữ hoàng, Công tước, phu nhân, kỵ sĩ, pháp sư.
                - Không dùng tại hạ, bổn tọa, sư huynh/sư muội, tông chủ cho nhân vật bản địa phương Tây nếu không có giao thoa.
                - Ngôi ba nam trung tính dùng tên hoặc hắn; chưa rõ giới tính thì không tự gán hắn/nàng. Tước hiệu chính thức thường đứng trước tên.
                </xung_ho>
                <ten_rieng>Khôi phục tên Hán hóa về Latin chỉ khi có glossary hoặc bằng chứng chắc; nếu không chắc, giữ phương án nhất quán và trung tính.</ten_rieng>
                <tinh_lien_tuc>Mọi Name, VietPhrase và Luật Nhân đã có là khóa liên chương; chỉ người dùng được sửa trong từ điển.</tinh_lien_tuc>
                <dinh_dang>Thoại xuống dòng, ngoặc kép; chỉ xuất bản dịch.</dinh_dang>
            """.trimIndent(),
        ),
        translationStyle(
            id = "context_scifi_system_v3",
            name = "Khoa huyễn / Game / Hệ thống",
            description = "Công nghệ, quân sự, game và hệ thống; thuật ngữ gọn, ổn định.",
            style = """
                <vai_tro>Dịch giả khoa học viễn tưởng, mạt thế, game và truyện hệ thống.</vai_tro>
                <muc_tieu>Diễn đạt gọn, rõ, chính xác thuật ngữ công nghệ/cấp bậc mà vẫn giữ nhịp văn học.</muc_tieu>
                <xung_ho>
                - Dùng tôi–anh/chị/ngài hoặc cấp dưới–chỉ huy theo tổ chức; cậu–tớ chỉ cho quan hệ ngang hàng đã rõ.
                - Không cổ phong hóa hệ thống, cơ giáp, quân hàm hoặc giao diện. Không biến lời kể thành thông báo hệ thống.
                - Ngôi ba giữ tên, chức vụ và giới tính đã biết; dữ kiện chưa đủ thì dùng tên/cách gọi trung tính.
                </xung_ho>
                <thuat_ngu>Giữ nhất quán kỹ năng, vật phẩm, chỉ số, đơn vị và tên giao diện theo glossary.</thuat_ngu>
                <tinh_lien_tuc>Mọi Name, VietPhrase và Luật Nhân đã có là khóa liên chương; chỉ người dùng được sửa trong từ điển.</tinh_lien_tuc>
                <dinh_dang>Thông báo hệ thống gọn; thoại xuống dòng trong ngoặc kép; chỉ xuất bản dịch.</dinh_dang>
            """.trimIndent(),
        ),
        translationStyle(
            id = "context_crossover_v3",
            name = "Xuyên không / Đồng nhân / Đa thế giới",
            description = "Tự đổi hệ từ vựng theo phó bản và nguồn gốc từng nhân vật.",
            style = """
                <vai_tro>Dịch giả đồng nhân, xuyên không, luân hồi và đa thế giới.</vai_tro>
                <muc_tieu>Giữ canon, Name và thuật ngữ riêng của từng thế giới; chuyển văn phong đúng lúc đổi phó bản/cảnh.</muc_tieu>
                <xung_ho>
                - Xác định thế giới hiện tại, thời đại, thân phận và nguồn gốc từng người nói trước khi chọn đại từ.
                - Không rải cậu–tớ vào cảnh cổ; không ép nhân vật phương Tây dùng tại hạ/bổn tọa; không đồng nhất mọi thế giới thành cổ phong.
                - Nhân vật xuyên giới chỉ giữ lối nói gốc khi nguồn thể hiện có chủ ý; chưa rõ giới tính thì dùng tên/cách gọi trung tính.
                </xung_ho>
                <ten_rieng>Ưu tiên glossary/Name và cách viết canon; không tự đoán tên Latin từ âm Hán nếu thiếu căn cứ.</ten_rieng>
                <tinh_lien_tuc>Mọi Name, VietPhrase và Luật Nhân đã có là khóa liên chương xuyên phó bản; chỉ người dùng được sửa trong từ điển.</tinh_lien_tuc>
                <dinh_dang>Thoại xuống dòng trong ngoặc kép; chỉ xuất bản dịch.</dinh_dang>
            """.trimIndent(),
        ),
        AiPromptCatalogTemplate(
            id = "summary_chapter_v1",
            taskType = AiTaskType.SUMMARIZE_CHAPTER,
            name = "Tóm tắt chương",
            description = "Tóm tắt sự kiện, nhân vật, xung đột và nút thắt.",
            prompt = AiPromptTemplate.DEFAULT_CHAPTER_SUMMARY,
        ),
        AiPromptCatalogTemplate(
            id = "summary_book_v1",
            taskType = AiTaskType.SUMMARIZE_BOOK,
            name = "Tóm tắt toàn truyện",
            description = "Tổng hợp cốt truyện theo tiến trình và không bịa dữ kiện.",
            prompt = """
                Tóm tắt tác phẩm dựa hoàn toàn trên nội dung được cung cấp. Trình bày tiền đề,
                các tuyến nhân vật, biến cố chính, quan hệ nhân quả, bước ngoặt và tình trạng
                hiện tại. Phân biệt dữ kiện chắc chắn với điểm chưa rõ. Không bịa nội dung
                của chương chưa được cung cấp.
            """.trimIndent(),
        ),
        AiPromptCatalogTemplate(
            id = "chat_reader_v1",
            taskType = AiTaskType.CHAT,
            name = "Trợ lý đọc truyện",
            description = "Trả lời dựa trên thư viện, chương và công cụ được cấp quyền.",
            prompt = """
                Bạn là trợ lý đọc truyện của người dùng. Trả lời rõ ràng bằng ngôn ngữ của họ,
                dựa trên dữ liệu sách và kết quả công cụ thực tế. Không bịa nội dung chưa đọc.
                Mọi thao tác thêm, sửa hoặc xóa dữ liệu phải được người dùng xác nhận trước.
            """.trimIndent(),
        ),
        AiPromptCatalogTemplate(
            id = "clean_selection_v1",
            taskType = AiTaskType.CLEAN_SELECTION,
            name = "Làm sạch đoạn chọn",
            description = "Xóa quảng cáo, mojibake và mảnh lặp nhưng giữ nguyên ý.",
            prompt = AiPromptTemplate.DEFAULT_CLEAN_SELECTION,
        ),
        AiPromptCatalogTemplate(
            id = "explain_selection_v1",
            taskType = AiTaskType.EXPLAIN_SELECTION,
            name = "Giải thích đoạn chọn",
            description = "Giải nghĩa theo ngữ cảnh truyện, không tiết lộ ngoài phạm vi được cấp.",
            prompt = """
                Giải thích đoạn văn người dùng chọn bằng ngôn ngữ rõ ràng. Dựa trên ngữ cảnh,
                từ điển và chương được cung cấp; làm rõ ý nghĩa, điển cố, quan hệ nhân vật hoặc
                thuật ngữ khi cần. Phân biệt dữ kiện với suy luận và không bịa tình tiết.
            """.trimIndent(),
        ),
        AiPromptCatalogTemplate(
            id = "text_factory_v1",
            taskType = AiTaskType.TEXT_FACTORY,
            name = "Xử lý văn bản",
            description = "Viết lại hoặc biến đổi đoạn chọn theo yêu cầu.",
            prompt = AiPromptTemplate.DEFAULT_TEXT_FACTORY,
        ),
        AiPromptCatalogTemplate(
            id = "rewrite_text_v1",
            taskType = AiTaskType.REWRITE_TEXT,
            name = "Viết lại văn bản",
            description = "Hiệu đính theo yêu cầu nhưng giữ nguyên dữ kiện và tính liên tục.",
            prompt = """
                Viết lại văn bản theo yêu cầu của người dùng trong khi giữ nguyên sự kiện,
                quan hệ nhân quả, tên riêng, số liệu và các thuật ngữ đã khóa. Không tự thêm
                tình tiết. Chỉ trả về văn bản hoàn chỉnh sau khi viết lại.
            """.trimIndent(),
        ),
        AiPromptCatalogTemplate(
            id = "authoring_director_v1",
            taskType = AiTaskType.AUTHORING_DIRECTOR,
            name = "Kiến trúc sư cốt truyện",
            description = "Hoàn thiện đề cương và triển khai hồi/quyển từ ý tưởng đã được duyệt.",
            prompt = AiPromptTemplate.DEFAULT_AUTHORING_DIRECTOR,
        ),
        AiPromptCatalogTemplate(
            id = "authoring_writer_v1",
            taskType = AiTaskType.AUTHORING_WRITER,
            name = "Nhà văn",
            description = "Viết chương theo đề cương, hồi/quyển và mạch truyện đã duyệt.",
            prompt = AiPromptTemplate.DEFAULT_AUTHORING_WRITER,
        ),
        AiPromptCatalogTemplate(
            id = "story_illustration_v1",
            taskType = AiTaskType.GENERATE_STORY_IMAGE,
            name = "Minh họa Wiki truyện",
            description = "Tạo ảnh nhân vật, trang bị, công pháp và bản đồ từ dữ kiện đã xác thực.",
            prompt = AiPromptTemplate.DEFAULT_STORY_IMAGE,
        ),
    )

    fun defaultPrompt(taskType: String): String = templates
        .firstOrNull { it.taskType == taskType }
        ?.prompt
        ?: "Follow the user's request faithfully. Return only the requested result."

    private fun translationStyle(
        id: String,
        name: String,
        description: String,
        style: String,
    ) = AiPromptCatalogTemplate(
        id = id,
        taskType = AiTaskType.TRANSLATE_CHAPTER,
        name = name,
        description = description,
        prompt = buildString {
            append(TranslationConstants.DEFAULT_PROMPT)
            append("\n\nHỒ SƠ PHONG CÁCH BỔ SUNG:\n")
            append(style)
        },
    )
}
