// 카드뉴스 콘텐츠 성격별 A/B/C/D 템플릿 정의.

export type TemplateId = 'A' | 'B' | 'C' | 'D';

export interface Template {
  id: TemplateId;
  label: string;
  tag: string;
  mainContent: string;
  designFeature: string;
}

export const TEMPLATES: Template[] = [
  {
    id: 'A',
    label: '공지·행사형',
    tag: 'Notice',
    mainContent:
      '학술컨퍼런스 개최 소식, 신규 실무진 모집, 일정·장소·등록 방법 등 시의성 있는 공지',
    designFeature:
      '날짜·시간·장소·신청 링크를 구역별로 명확히 나눠 한눈에 파악되는 정보 전달형 레이아웃',
  },
  {
    id: 'B',
    label: '인터뷰·인물형',
    tag: 'Interview',
    mainContent: '각계 인사 인터뷰 등 인물을 중심으로 한 이야기',
    designFeature:
      '인물 사진을 시선이 먼저 닿는 자리에 배치하고, 핵심 발언은 인용구 블록으로 강조',
  },
  {
    id: 'C',
    label: '지식·교양형',
    tag: 'Insight',
    mainContent: '정기 컨텐츠, 학술 용어 해설, 최신 연구 동향 브리핑',
    designFeature:
      '여러 장에 걸쳐 설명 텍스트와 인포그래픽을 번갈아 배치해 정보량이 많아도 부담 없이 읽히도록 구성',
  },
  {
    id: 'D',
    label: '일반 소식형',
    tag: 'Journal',
    mainContent:
      '학회 근황, 언론 보도 스크랩, 산학협력 소식, 회원 경조사 등 가볍게 전하는 소식',
    designFeature:
      '매거진·뉴스레터풍의 단정하고 여백이 있는 레이아웃으로 부담 없이 훑어보게 구성',
  },
];
