package ch.adibilis.jtg.parser.fixtures;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class TestFileUploadController {

    @PostMapping("/upload")
    public SimpleDto upload(@RequestPart MultipartFile file, @RequestParam String description) { return null; }

    @PostMapping("/multi")
    public SimpleDto multiUpload(@RequestParam MultipartFile avatar, @RequestParam String name) { return null; }
}
